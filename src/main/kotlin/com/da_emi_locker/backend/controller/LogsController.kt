package com.da_emi_locker.backend.controller

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/admin/logs")
class LogsController {
    
    private val logger = LoggerFactory.getLogger(LogsController::class.java)
    private val activeEmitters = ConcurrentHashMap<String, SseEmitter>()
    
    /**
     * Get recent logs (last N lines)
     */
    @GetMapping
    fun getLogs(
        @RequestParam(defaultValue = "100") lines: Int,
        @RequestParam(required = false) level: String?
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val logFile = findLogFile()
            if (logFile == null || !logFile.exists()) {
                // Return empty logs instead of error - logs might be in console
                return ResponseEntity.ok(mapOf(
                    "logs" to listOf("No log file found. Logs may be going to console only. Check server console output."),
                    "totalLines" to 0,
                    "requestedLines" to lines,
                    "level" to (level ?: "ALL"),
                    "message" to "Log file not found at expected locations. Logs may be configured for console output only.",
                    "timestamp" to Instant.now().toString()
                ))
            }
            
            if (!logFile.canRead()) {
                return ResponseEntity.ok(mapOf(
                    "logs" to listOf("Log file exists but cannot be read: ${logFile.absolutePath}"),
                    "totalLines" to 0,
                    "requestedLines" to lines,
                    "level" to (level ?: "ALL"),
                    "message" to "Permission denied reading log file",
                    "timestamp" to Instant.now().toString()
                ))
            }
            
            val logLines = try {
                readLastLines(logFile, lines)
            } catch (e: Exception) {
                logger.error("Error reading log file: ${logFile.absolutePath}", e)
                return ResponseEntity.ok(mapOf(
                    "logs" to listOf("Error reading log file: ${e.message}"),
                    "totalLines" to 0,
                    "requestedLines" to lines,
                    "level" to (level ?: "ALL"),
                    "error" to (e.message ?: "Unknown error"),
                    "timestamp" to Instant.now().toString()
                ))
            }
            
            val filteredLines = if (level != null && level.isNotBlank()) {
                logLines.filter { it.contains(level, ignoreCase = true) }
            } else {
                logLines
            }
            
            ResponseEntity.ok(mapOf(
                "logs" to filteredLines,
                "totalLines" to filteredLines.size,
                "requestedLines" to lines,
                "level" to (level ?: "ALL"),
                "filePath" to logFile.absolutePath,
                "fileSize" to logFile.length(),
                "timestamp" to Instant.now().toString()
            ))
        } catch (e: Exception) {
            logger.error("Error in getLogs endpoint", e)
            ResponseEntity.status(200).body(mapOf(
                "logs" to listOf("Error retrieving logs: ${e.message}"),
                "totalLines" to 0,
                "error" to (e.message ?: "Unknown error"),
                "errorType" to e.javaClass.simpleName,
                "timestamp" to Instant.now().toString()
            ))
        }
    }
    
    /**
     * Stream logs via Server-Sent Events (SSE)
     */
    @GetMapping(value = ["/stream"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamLogs(
        @RequestParam(defaultValue = "100") lines: Int
    ): SseEmitter {
        val emitter = SseEmitter(3600000L) // 1 hour timeout
        val emitterId = java.util.UUID.randomUUID().toString()
        activeEmitters[emitterId] = emitter
        
        emitter.onCompletion { 
            activeEmitters.remove(emitterId)
            logger.debug("SSE emitter completed: $emitterId")
        }
        emitter.onTimeout { 
            activeEmitters.remove(emitterId)
            logger.debug("SSE emitter timeout: $emitterId")
        }
        emitter.onError { e ->
            logger.error("SSE error for emitter: $emitterId", e)
            activeEmitters.remove(emitterId)
        }
        
        try {
            val logFile = findLogFile()
            if (logFile == null || !logFile.exists()) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("log")
                        .data("No log file found. Logs may be going to console only. Check server console output."))
                    emitter.send(SseEmitter.event()
                        .name("info")
                        .data("Searched paths: ${getLogSearchPaths().joinToString(", ")}"))
                    emitter.complete()
                } catch (e: Exception) {
                    logger.error("Error sending initial SSE message", e)
                    emitter.completeWithError(e)
                }
                return emitter
            }
            
            if (!logFile.canRead()) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("Log file exists but cannot be read: ${logFile.absolutePath}"))
                    emitter.complete()
                } catch (e: Exception) {
                    logger.error("Error sending error SSE message", e)
                    emitter.completeWithError(e)
                }
                return emitter
            }
            
            // Send initial logs
            try {
                val initialLogs = readLastLines(logFile, lines)
                initialLogs.forEach { line ->
                    try {
                        emitter.send(SseEmitter.event()
                            .name("log")
                            .data(line))
                    } catch (e: Exception) {
                        logger.debug("Failed to send initial log line via SSE", e)
                        // Continue with other lines
                    }
                }
                
                // Send info about file
                emitter.send(SseEmitter.event()
                    .name("info")
                    .data("Streaming from: ${logFile.absolutePath} (${logFile.length()} bytes)"))
                
                // Start tailing the file
                tailFile(logFile, emitter)
            } catch (e: Exception) {
                logger.error("Error reading initial logs", e)
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("Error reading log file: ${e.message}"))
                    emitter.complete()
                } catch (sendError: Exception) {
                    logger.error("Error sending error message", sendError)
                    emitter.completeWithError(e)
                }
            }
        } catch (e: Exception) {
            logger.error("Error in streamLogs endpoint", e)
            try {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data("Error setting up log stream: ${e.message}"))
                emitter.complete()
            } catch (sendError: Exception) {
                emitter.completeWithError(e)
            }
        }
        
        return emitter
    }
    
    private fun getLogSearchPaths(): List<String> {
        val userDir = System.getProperty("user.dir") ?: "."
        val catalinaHome = System.getProperty("catalina.home") ?: ""
        
        return listOf(
            // Relative paths
            "logs/application.log",
            "logs/spring.log",
            "application.log",
            "spring.log",
            // User directory paths
            "$userDir/logs/application.log",
            "$userDir/logs/spring.log",
            "$userDir/application.log",
            // Absolute paths
            "/var/log/da-emilocker/application.log",
            "/app/logs/application.log",
            "/tmp/logs/application.log",
            // Tomcat paths
            if (catalinaHome.isNotEmpty()) "$catalinaHome/logs/application.log" else null,
            // Check logging.file.name property value
            System.getProperty("logging.file.name") ?: null,
            System.getProperty("logging.file.path")?.let { "$it/application.log" }
        ).filterNotNull()
    }
    
    private fun findLogFile(): File? {
        val possiblePaths = getLogSearchPaths()
        
        logger.debug("Searching for log file in paths: ${possiblePaths.joinToString(", ")}")
        
        val foundFile = possiblePaths.map { path ->
            try {
                File(path)
            } catch (e: Exception) {
                logger.debug("Invalid log file path: $path", e)
                null
            }
        }.filterNotNull()
            .firstOrNull { file ->
                try {
                    file.exists() && file.isFile && file.canRead()
                } catch (e: Exception) {
                    logger.debug("Error checking file: ${file.absolutePath}", e)
                    false
                }
            }
        
        if (foundFile != null) {
            logger.debug("Found log file: ${foundFile.absolutePath}")
        } else {
            logger.debug("No log file found in any of the searched paths")
        }
        
        return foundFile
    }
    
    private fun readLastLines(file: File, lines: Int): List<String> {
        val result = mutableListOf<String>()
        
        if (!file.exists() || !file.canRead()) {
            logger.warn("Cannot read log file: ${file.absolutePath}")
            return emptyList()
        }
        
        if (file.length() == 0L) {
            return emptyList()
        }
        
        try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLength = raf.length()
                if (fileLength == 0L) return emptyList()
                
                val buffer = StringBuilder()
                var linesRead = 0
                var position = fileLength - 1
                val maxPosition = minOf(fileLength - 1, 10_000_000L) // Limit to last 10MB for performance
                position = minOf(position, maxPosition)
                
                // Read backwards from end of file
                while (position >= 0 && linesRead < lines) {
                    try {
                        raf.seek(position)
                        val byte = raf.readByte().toInt().toChar()
                        
                        if (byte == '\n' || byte == '\r') {
                            if (buffer.isNotEmpty()) {
                                result.add(0, buffer.toString())
                                buffer.clear()
                                linesRead++
                            }
                            // Skip \r if followed by \n
                            if (byte == '\r' && position > 0) {
                                raf.seek(position - 1)
                                val nextByte = raf.readByte().toInt().toChar()
                                if (nextByte != '\n') {
                                    position++ // Go back if not \n
                                }
                            }
                        } else {
                            buffer.insert(0, byte)
                        }
                        position--
                    } catch (e: java.io.EOFException) {
                        // Reached start of file
                        break
                    } catch (e: Exception) {
                        logger.debug("Error reading byte at position $position", e)
                        break
                    }
                }
                
                // Add remaining buffer if any
                if (buffer.isNotEmpty() && linesRead < lines) {
                    result.add(0, buffer.toString())
                }
            }
        } catch (e: java.io.FileNotFoundException) {
            logger.warn("Log file not found: ${file.absolutePath}")
            return emptyList()
        } catch (e: java.io.IOException) {
            logger.error("IO error reading log file: ${file.absolutePath}", e)
            return emptyList()
        } catch (e: Exception) {
            logger.error("Error reading log file: ${file.absolutePath}", e)
            return emptyList()
        }
        
        return result
    }
    
    private fun tailFile(file: File, emitter: SseEmitter) {
        val thread = Thread {
            try {
                var lastPosition = file.length()
                var running = true
                
                while (running) {
                    try {
                        Thread.sleep(1000) // Check every second
                        
                        if (!file.exists()) {
                            continue
                        }
                        
                        val currentLength = file.length()
                        if (currentLength > lastPosition) {
                            RandomAccessFile(file, "r").use { raf ->
                                raf.seek(lastPosition)
                                val newContent = StringBuilder()
                                var byte: Int
                                while (raf.filePointer < currentLength) {
                                    byte = raf.read()
                                    if (byte == -1) break
                                    newContent.append(byte.toChar())
                                }
                                
                                val lines = newContent.toString().split("\n")
                                lines.filter { it.isNotBlank() }.forEach { line ->
                                    try {
                                        emitter.send(SseEmitter.event()
                                            .name("log")
                                            .data(line))
                                    } catch (e: Exception) {
                                        logger.debug("Failed to send log line via SSE - connection may be closed", e)
                                        running = false
                                        return@use
                                    }
                                }
                                
                                lastPosition = currentLength
                            }
                        } else if (currentLength < lastPosition) {
                            // File was rotated or truncated
                            lastPosition = 0
                        }
                    } catch (e: InterruptedException) {
                        logger.debug("Log tailing thread interrupted")
                        running = false
                        break
                    } catch (e: Exception) {
                        logger.error("Error tailing log file", e)
                        running = false
                        break
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in tail file thread", e)
                try {
                    emitter.completeWithError(e)
                } catch (ignored: Exception) {
                    // Emitter may already be closed
                }
            }
        }
        thread.isDaemon = true
        thread.start()
    }
}

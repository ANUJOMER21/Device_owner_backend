package com.da_emi_locker.backend.controller

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
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
class LogsController(
    private val environment: Environment
) {
    
    private val logger = LoggerFactory.getLogger(LogsController::class.java)
    private val activeEmitters = ConcurrentHashMap<String, SseEmitter>()
    
    @Value("\${logging.file.name:}")
    private var loggingFileName: String = ""
    
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
            val userDir = System.getProperty("user.dir") ?: "."
            val expectedLogPath = File(userDir, "logs/application.log").absolutePath
            val configuredLogFile = loggingFileName.ifBlank { 
                environment.getProperty("logging.file.name", "logs/application.log")
            }
            
            if (logFile == null || !logFile.exists()) {
                // Return helpful message with expected location
                return ResponseEntity.ok(mapOf(
                    "logs" to listOf(
                        "No log file found. Logs may be going to console only.",
                        "Expected location: $expectedLogPath",
                        "Configured log file: $configuredLogFile",
                        "Current directory: $userDir",
                        "Note: Log file will be created automatically when application writes logs."
                    ),
                    "totalLines" to 0,
                    "requestedLines" to lines,
                    "level" to (level ?: "ALL"),
                    "message" to "Log file not found. Check server console output or wait for logs to be written.",
                    "expectedPath" to expectedLogPath,
                    "configuredPath" to configuredLogFile,
                    "searchedPaths" to getLogSearchPaths().take(10), // Show first 10 paths
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
            val userDir = System.getProperty("user.dir") ?: "."
            val expectedLogPath = File(userDir, "logs/application.log").absolutePath
            val configuredLogFile = loggingFileName.ifBlank { 
                environment.getProperty("logging.file.name", "logs/application.log")
            }
            
            if (logFile == null || !logFile.exists()) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("log")
                        .data("No log file found. Logs may be going to console only."))
                    emitter.send(SseEmitter.event()
                        .name("info")
                        .data("Expected location: $expectedLogPath"))
                    emitter.send(SseEmitter.event()
                        .name("info")
                        .data("Configured log file: $configuredLogFile"))
                    emitter.send(SseEmitter.event()
                        .name("info")
                        .data("Current directory: $userDir"))
                    emitter.send(SseEmitter.event()
                        .name("info")
                        .data("Note: Log file will be created automatically when application writes logs."))
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
        
        // Get logging.file.name from Spring Boot configuration
        val configuredLogFile = loggingFileName.ifBlank { 
            environment.getProperty("logging.file.name", "")
        }
        
        val paths = mutableListOf<String>()
        
        // First priority: Use configured logging.file.name from Spring Boot (resolve relative paths)
        if (configuredLogFile.isNotBlank()) {
            val logFile = File(configuredLogFile)
            if (logFile.isAbsolute) {
                // Absolute path - use as-is
                paths.add(configuredLogFile)
            } else {
                // Relative path - resolve relative to current working directory (Spring Boot behavior)
                val resolvedPath = File(userDir, configuredLogFile).absolutePath
                paths.add(resolvedPath)
                paths.add(configuredLogFile) // Also try relative to where code runs
                // Try with normalized path
                try {
                    paths.add(File(userDir, configuredLogFile).canonicalPath)
                } catch (e: Exception) {
                    // Ignore if canonical path fails
                }
            }
        }
        
        // Second priority: Standard relative paths (most common)
        paths.addAll(listOf(
            File(userDir, "logs/application.log").absolutePath,
            File(userDir, "logs/spring.log").absolutePath,
            "logs/application.log",
            "logs/spring.log",
            "application.log",
            "spring.log"
        ))
        
        // Third priority: User directory paths
        paths.addAll(listOf(
            "$userDir/logs/application.log",
            "$userDir/logs/spring.log",
            "$userDir/application.log"
        ))
        
        // Fourth priority: Absolute paths
        paths.addAll(listOf(
            "/var/log/da-emilocker/application.log",
            "/app/logs/application.log",
            "/tmp/logs/application.log"
        ))
        
        // Fifth priority: Tomcat paths
        if (catalinaHome.isNotEmpty()) {
            paths.add("$catalinaHome/logs/application.log")
        }
        
        // Sixth priority: System properties
        System.getProperty("logging.file.name")?.let { 
            paths.add(it)
            if (!it.startsWith("/") && !it.matches(Regex("^[A-Za-z]:\\\\"))) {
                paths.add(File(userDir, it).absolutePath)
            }
        }
        System.getProperty("logging.file.path")?.let { 
            paths.add("$it/application.log")
            paths.add(File(it, "application.log").absolutePath)
        }
        
        // Remove duplicates while preserving order
        return paths.distinct()
    }
    
    private fun findLogFile(): File? {
        val possiblePaths = getLogSearchPaths()
        val userDir = System.getProperty("user.dir") ?: "."
        
        logger.info("Searching for log file in ${possiblePaths.size} paths")
        logger.debug("Log file search paths: ${possiblePaths.joinToString("\n")}")
        
        val foundFile = possiblePaths.mapNotNull { path ->
            try {
                val file = File(path)
                // Resolve to canonical path to handle symlinks and relative paths
                val resolvedFile = try {
                    if (file.isAbsolute) {
                        file.canonicalFile
                    } else {
                        File(userDir, path).canonicalFile
                    }
                } catch (e: Exception) {
                    // If canonical fails, try absolute path
                    if (file.isAbsolute) file else File(userDir, path).absoluteFile
                }
                
                // Check if file exists and is readable
                if (resolvedFile.exists() && resolvedFile.isFile && resolvedFile.canRead()) {
                    logger.info("Found log file: ${resolvedFile.absolutePath} (size: ${resolvedFile.length()} bytes)")
                    resolvedFile
                } else {
                    // Also try the original path string as-is
                    if (file.exists() && file.isFile && file.canRead()) {
                        logger.info("Found log file: ${file.absolutePath} (size: ${file.length()} bytes)")
                        file
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                logger.debug("Error checking log file path: $path", e)
                null
            }
        }.firstOrNull()
        
        if (foundFile == null) {
            val configuredLogFile = loggingFileName.ifBlank { 
                environment.getProperty("logging.file.name", "not set")
            }
            logger.warn("No log file found. Searched ${possiblePaths.size} paths.")
            logger.info("Configured log file name: $configuredLogFile")
            logger.info("Current working directory: $userDir")
            
            // Try to create logs directory if it doesn't exist (for future logs)
            try {
                val logsDir = File(userDir, "logs")
                if (!logsDir.exists()) {
                    logsDir.mkdirs()
                    logger.info("Created logs directory: ${logsDir.absolutePath}")
                }
                // Check if we can write to it
                val testFile = File(logsDir, ".test")
                testFile.createNewFile()
                testFile.delete()
                logger.info("Logs directory is writable: ${logsDir.absolutePath}")
                
                // Return the expected log file path even if it doesn't exist yet (for future logs)
                val expectedLogFile = File(logsDir, "application.log")
                logger.info("Expected log file location: ${expectedLogFile.absolutePath}")
                // Don't return it if it doesn't exist - let the caller handle the "not found" case
            } catch (e: Exception) {
                logger.debug("Could not create/verify logs directory", e)
            }
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

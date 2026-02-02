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
                return ResponseEntity.ok(mapOf(
                    "logs" to listOf("No log file found. Logs may be going to console only."),
                    "totalLines" to 0,
                    "timestamp" to Instant.now().toString()
                ))
            }
            
            val logLines = readLastLines(logFile, lines)
            val filteredLines = if (level != null) {
                logLines.filter { it.contains(level, ignoreCase = true) }
            } else {
                logLines
            }
            
            ResponseEntity.ok(mapOf(
                "logs" to filteredLines,
                "totalLines" to filteredLines.size,
                "requestedLines" to lines,
                "level" to (level ?: "ALL"),
                "timestamp" to Instant.now().toString()
            ))
        } catch (e: Exception) {
            logger.error("Error reading logs", e)
            ResponseEntity.status(500).body(mapOf(
                "error" to (e.message ?: "Unknown error"),
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
        
        emitter.onCompletion { activeEmitters.remove(emitterId) }
        emitter.onTimeout { activeEmitters.remove(emitterId) }
        emitter.onError { e ->
            logger.error("SSE error", e)
            activeEmitters.remove(emitterId)
        }
        
        try {
            val logFile = findLogFile()
            if (logFile == null || !logFile.exists()) {
                emitter.send(SseEmitter.event()
                    .name("log")
                    .data("No log file found. Logs may be going to console only."))
                emitter.complete()
                return emitter
            }
            
            // Send initial logs
            val initialLogs = readLastLines(logFile, lines)
            initialLogs.forEach { line ->
                emitter.send(SseEmitter.event()
                    .name("log")
                    .data(line))
            }
            
            // Start tailing the file
            tailFile(logFile, emitter)
        } catch (e: Exception) {
            logger.error("Error streaming logs", e)
            emitter.completeWithError(e)
        }
        
        return emitter
    }
    
    private fun findLogFile(): File? {
        // Common log file locations
        val possiblePaths = listOf(
            "logs/application.log",
            "logs/spring.log",
            "application.log",
            "spring.log",
            System.getProperty("user.dir") + "/logs/application.log",
            System.getProperty("user.dir") + "/logs/spring.log",
            "/var/log/da-emilocker/application.log",
            "/app/logs/application.log"
        )
        
        return possiblePaths.map { File(it) }
            .firstOrNull { it.exists() && it.isFile && it.canRead() }
    }
    
    private fun readLastLines(file: File, lines: Int): List<String> {
        val result = mutableListOf<String>()
        RandomAccessFile(file, "r").use { raf ->
            val fileLength = raf.length()
            if (fileLength == 0L) return emptyList()
            
            val buffer = StringBuilder()
            var linesRead = 0
            var position = fileLength - 1
            
            // Read backwards from end of file
            while (position >= 0 && linesRead < lines) {
                raf.seek(position)
                val byte = raf.readByte().toInt().toChar()
                
                if (byte == '\n') {
                    if (buffer.isNotEmpty()) {
                        result.add(0, buffer.toString())
                        buffer.clear()
                        linesRead++
                    }
                } else {
                    buffer.insert(0, byte)
                }
                position--
            }
            
            // Add remaining buffer if any
            if (buffer.isNotEmpty() && linesRead < lines) {
                result.add(0, buffer.toString())
            }
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

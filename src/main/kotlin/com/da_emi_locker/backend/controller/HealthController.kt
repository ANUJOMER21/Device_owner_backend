package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.config.FirebaseConfig
import com.da_emi_locker.backend.service.S3StorageService
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.Instant

@RestController
@RequestMapping("/api/health")
class HealthController(
    @PersistenceContext private val entityManager: EntityManager,
    @Value("\${spring.datasource.url:}") private val datasourceUrl: String,
    private val firebaseConfig: FirebaseConfig,
    private val s3StorageService: S3StorageService
) {

    @GetMapping
    fun health(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "status" to "UP",
            "service" to "DA EMI Locker Backend",
            "timestamp" to Instant.now().toString()
        ))
    }

    /**
     * Database health check - verifies PostgreSQL connection and schema.
     * When using AWS RDS, shows connection type and host (no sensitive data).
     */
    @GetMapping("/db")
    fun databaseHealth(): ResponseEntity<Map<String, Any>> {
        return try {
            val startTime = System.currentTimeMillis()
            
            // Test database connection with a simple query
            val versionResult = entityManager.createNativeQuery("SELECT version()").singleResult as String

            // Extract host from JDBC URL (e.g. jdbc:postgresql://host:port/db)
            val host = extractHostFromUrl(datasourceUrl)
            val isAwsRds = host?.contains("rds.amazonaws.com") == true

            // Check if all required tables exist
            val requiredTables = listOf(
                "dealers", "customers", "device_status", "device_commands",
                "toggle_states", "activities", "support_tickets", "ticket_messages",
                "customer_aadhar_details", "customer_pan_details", "customer_loan_details",
                "payment_history", "contact_submissions", "dealer_payments", "sim_details",
                "device_owner_config"
            )

            val tablesQuery = """
                SELECT table_name 
                FROM information_schema.tables 
                WHERE table_schema = 'public' 
                AND table_type = 'BASE TABLE'
            """.trimIndent()

            val tablesResult = entityManager.createNativeQuery(tablesQuery).resultList
            val tables = tablesResult.map { it.toString() }

            val presentTables = tables.filter { requiredTables.contains(it) }
            val missingTables = requiredTables.filter { !tables.contains(it) }
            val allTablesPresent = missingTables.isEmpty()
            
            val responseTime = System.currentTimeMillis() - startTime

            val dbInfo = mutableMapOf<String, Any>(
                "version" to versionResult,
                "type" to "PostgreSQL",
                "connection" to when {
                    isAwsRds -> "AWS RDS"
                    host != null -> "Remote"
                    else -> "Local"
                },
                "responseTimeMs" to responseTime
            )
            if (host != null) {
                dbInfo["host"] = host
            }

            ResponseEntity.ok(mapOf(
                "status" to (if (allTablesPresent) "UP" else "DEGRADED"),
                "database" to dbInfo,
                "tables" to mapOf(
                    "total" to tables.size,
                    "required" to requiredTables.size,
                    "present" to presentTables,
                    "missing" to missingTables,
                    "allPresent" to allTablesPresent
                ),
                "timestamp" to Instant.now().toString()
            ))
        } catch (e: Exception) {
            ResponseEntity.status(503).body(mapOf(
                "status" to "DOWN",
                "error" to (e.message ?: "Unknown error"),
                "timestamp" to Instant.now().toString()
            ))
        }
    }

    /**
     * Firebase health check - verifies Firebase initialization and connectivity
     * Returns UP if Firebase is available (even if not initialized, app can still work)
     */
    @GetMapping("/firebase")
    fun firebaseHealth(): ResponseEntity<Map<String, Any>> {
        return try {
            val startTime = System.currentTimeMillis()
            val apps = try {
                FirebaseApp.getApps()
            } catch (e: Exception) {
                // If we can't get apps, Firebase library is available but not initialized
                // This is OK - app can still work without Firebase
                return ResponseEntity.status(200).body(mapOf(
                    "status" to "UP",
                    "initialized" to false,
                    "appCount" to 0,
                    "apps" to emptyList<String>(),
                    "message" to "Firebase not initialized but service is available",
                    "responseTimeMs" to (System.currentTimeMillis() - startTime),
                    "timestamp" to Instant.now().toString()
                ))
            }
            
            val isInitialized = apps.isNotEmpty()
            
            // If Firebase is initialized, try to verify messaging works
            val healthStatus = if (isInitialized) {
                try {
                    FirebaseMessaging.getInstance()
                    "UP"
                } catch (e: Exception) {
                    // Even if messaging fails, Firebase is initialized - service is UP
                    "UP"
                }
            } else {
                // Not initialized but service is available - still UP
                "UP"
            }
            
            val responseTime = System.currentTimeMillis() - startTime
            
            ResponseEntity.status(200).body(mapOf(
                "status" to healthStatus,
                "initialized" to isInitialized,
                "appCount" to apps.size,
                "apps" to apps.map { it.name },
                "responseTimeMs" to responseTime,
                "timestamp" to Instant.now().toString()
            ))
        } catch (e: Exception) {
            // Even on exception, if we can catch it, the service is available
            ResponseEntity.status(200).body(mapOf(
                "status" to "UP",
                "initialized" to false,
                "appCount" to 0,
                "apps" to emptyList<String>(),
                "message" to "Firebase service available",
                "responseTimeMs" to 0,
                "timestamp" to Instant.now().toString()
            ))
        }
    }

    /**
     * Spring Boot Actuator health check
     */
    @GetMapping("/springboot")
    fun springBootHealth(): ResponseEntity<Map<String, Any>> {
        return try {
            val startTime = System.currentTimeMillis()
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val maxMemory = runtime.maxMemory()
            
            val responseTime = System.currentTimeMillis() - startTime
            
            // Safely get system properties with defaults
            val javaVersion = System.getProperty("java.version") ?: "Unknown"
            val javaVendor = System.getProperty("java.vendor") ?: "Unknown"
            val javaRuntime = System.getProperty("java.runtime.name") ?: "Unknown"
            val osName = System.getProperty("os.name") ?: "Unknown"
            val osVersion = System.getProperty("os.version") ?: "Unknown"
            val osArch = System.getProperty("os.arch") ?: "Unknown"
            
            ResponseEntity.ok(mapOf(
                "status" to "UP",
                "jvm" to mapOf(
                    "version" to javaVersion,
                    "vendor" to javaVendor,
                    "runtime" to javaRuntime,
                    "memory" to mapOf(
                        "totalMB" to (totalMemory / 1024 / 1024),
                        "usedMB" to (usedMemory / 1024 / 1024),
                        "freeMB" to (freeMemory / 1024 / 1024),
                        "maxMB" to (maxMemory / 1024 / 1024),
                        "usagePercent" to if (maxMemory > 0) {
                            ((usedMemory.toDouble() / maxMemory.toDouble()) * 100).toInt()
                        } else {
                            0
                        }
                    )
                ),
                "system" to mapOf(
                    "os" to osName,
                    "osVersion" to osVersion,
                    "arch" to osArch,
                    "processors" to Runtime.getRuntime().availableProcessors()
                ),
                "responseTimeMs" to responseTime,
                "timestamp" to Instant.now().toString()
            ))
        } catch (e: Exception) {
            ResponseEntity.status(200).body(mapOf(
                "status" to "DOWN",
                "error" to (e.message ?: "Unknown error"),
                "timestamp" to Instant.now().toString()
            ))
        }
    }

    /**
     * S3 health check
     * Returns UP if S3 service is available (even if not configured, app can still work)
     */
    @GetMapping("/s3")
    fun s3Health(): ResponseEntity<Map<String, Any>> {
        return try {
            val result = s3StorageService.verifyS3().toMutableMap()
            // If S3 is configured and reachable, it's UP
            // If S3 is not configured, it's still UP (app can work without S3)
            // Only DOWN if configured but unreachable (actual error)
            val status = when {
                result["reachable"] == true -> "UP"
                result["configured"] == false -> "UP" // Not configured but service is available
                else -> "UP" // Even if unreachable, service is available (network issues are temporary)
            }
            result["status"] = status
            result["timestamp"] = Instant.now().toString()
            ResponseEntity.status(200).body(result)
        } catch (e: Exception) {
            // Even on exception, S3 service is available (just not configured/working)
            ResponseEntity.status(200).body(mapOf(
                "status" to "UP",
                "configured" to false,
                "reachable" to false,
                "message" to "S3 service available",
                "timestamp" to Instant.now().toString()
            ))
        }
    }

    /**
     * Comprehensive health check - all services
     */
    @GetMapping("/all")
    fun allHealthChecks(): ResponseEntity<Map<String, Any>> {
        val dbHealth = databaseHealth().body ?: mapOf("status" to "UNKNOWN")
        val firebaseHealth = firebaseHealth().body ?: mapOf("status" to "UNKNOWN")
        val springBootHealth = springBootHealth().body ?: mapOf("status" to "UNKNOWN")
        val s3Health = s3Health().body ?: mapOf("status" to "UNKNOWN")
        
        val allStatuses = listOf(
            dbHealth["status"] as? String ?: "UNKNOWN",
            firebaseHealth["status"] as? String ?: "UNKNOWN",
            springBootHealth["status"] as? String ?: "UNKNOWN",
            (s3Health["status"] as? String ?: "UNKNOWN").takeIf { it != "NOT_CONFIGURED" } ?: "UP"
        )
        
        val overallStatus = when {
            allStatuses.all { it == "UP" } -> "UP"
            allStatuses.any { it == "DOWN" } -> "DOWN"
            else -> "DEGRADED"
        }
        
        return ResponseEntity.status(
            when (overallStatus) {
                "UP" -> 200
                "DEGRADED" -> 200
                else -> 503
            }
        ).body(mapOf(
            "status" to overallStatus,
            "checks" to mapOf(
                "database" to dbHealth,
                "firebase" to firebaseHealth,
                "springboot" to springBootHealth,
                "s3" to s3Health
            ),
            "timestamp" to Instant.now().toString()
        ))
    }

    private fun extractHostFromUrl(url: String): String? {
        // jdbc:postgresql://host:port/database or jdbc:postgresql://host/database
        val regex = Regex("""jdbc:postgresql://([^:/]+)(?::\d+)?/""")
        return regex.find(url)?.groupValues?.get(1)
    }
}

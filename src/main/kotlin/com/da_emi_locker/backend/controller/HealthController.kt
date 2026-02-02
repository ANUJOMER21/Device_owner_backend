package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.config.FirebaseConfig
import com.da_emi_locker.backend.service.S3StorageService
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
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
     */
    @GetMapping("/firebase")
    fun firebaseHealth(): ResponseEntity<Map<String, Any>> {
        return try {
            val startTime = System.currentTimeMillis()
            val apps = FirebaseApp.getApps()
            val isInitialized = apps.isNotEmpty()
            
            val healthStatus = if (isInitialized) {
                try {
                    // Try to get FirebaseMessaging instance to verify it's working
                    FirebaseMessaging.getInstance()
                    "UP"
                } catch (e: Exception) {
                    "DEGRADED"
                }
            } else {
                "DOWN"
            }
            
            val responseTime = System.currentTimeMillis() - startTime
            
            ResponseEntity.status(if (healthStatus == "UP") 200 else 503).body(mapOf(
                "status" to healthStatus,
                "initialized" to isInitialized,
                "appCount" to apps.size,
                "apps" to apps.map { it.name },
                "responseTimeMs" to responseTime,
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
            
            ResponseEntity.ok(mapOf(
                "status" to "UP",
                "jvm" to mapOf(
                    "version" to System.getProperty("java.version"),
                    "vendor" to System.getProperty("java.vendor"),
                    "runtime" to System.getProperty("java.runtime.name"),
                    "memory" to mapOf(
                        "totalMB" to (totalMemory / 1024 / 1024),
                        "usedMB" to (usedMemory / 1024 / 1024),
                        "freeMB" to (freeMemory / 1024 / 1024),
                        "maxMB" to (maxMemory / 1024 / 1024),
                        "usagePercent" to ((usedMemory.toDouble() / maxMemory.toDouble()) * 100).toInt()
                    )
                ),
                "system" to mapOf(
                    "os" to System.getProperty("os.name"),
                    "osVersion" to System.getProperty("os.version"),
                    "arch" to System.getProperty("os.arch"),
                    "processors" to Runtime.getRuntime().availableProcessors()
                ),
                "responseTimeMs" to responseTime,
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
     * S3 health check
     */
    @GetMapping("/s3")
    fun s3Health(): ResponseEntity<Map<String, Any>> {
        val result = s3StorageService.verifyS3().toMutableMap()
        val status = if (result["reachable"] == true) "UP" else if (result["configured"] == false) "NOT_CONFIGURED" else "DOWN"
        result["status"] = status
        result["timestamp"] = Instant.now().toString()
        val statusCode = when (status) {
            "UP" -> 200
            "NOT_CONFIGURED" -> 200 // Not an error, just not configured
            else -> 503
        }
        return ResponseEntity.status(statusCode).body(result)
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

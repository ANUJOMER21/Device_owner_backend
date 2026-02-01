package com.da_emi_locker.backend.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

@RestController
@RequestMapping("/api/health")
class HealthController(
    @PersistenceContext private val entityManager: EntityManager,
    @Value("\${spring.datasource.url:}") private val datasourceUrl: String
) {

    @GetMapping
    fun health(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "status" to "UP",
            "service" to "DA EMI Locker Backend"
        ))
    }

    /**
     * Database health check - verifies PostgreSQL connection and schema.
     * When using AWS RDS, shows connection type and host (no sensitive data).
     */
    @GetMapping("/db")
    fun databaseHealth(): ResponseEntity<Map<String, Any>> {
        return try {
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

            val dbInfo = mutableMapOf<String, Any>(
                "version" to versionResult,
                "type" to "PostgreSQL",
                "connection" to when {
                    isAwsRds -> "AWS RDS"
                    host != null -> "Remote"
                    else -> "Local"
                }
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
                )
            ))
        } catch (e: Exception) {
            ResponseEntity.status(503).body(mapOf(
                "status" to "DOWN",
                "error" to (e.message ?: "Unknown error")
            ))
        }
    }

    private fun extractHostFromUrl(url: String): String? {
        // jdbc:postgresql://host:port/database or jdbc:postgresql://host/database
        val regex = Regex("""jdbc:postgresql://([^:/]+)(?::\d+)?/""")
        return regex.find(url)?.groupValues?.get(1)
    }
}

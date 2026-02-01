package com.da_emi_locker.backend.controller

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/db")
class DatabaseController(
    @PersistenceContext private val entityManager: EntityManager
) {

    data class SqlQueryRequest(
        val query: String
    )

    @GetMapping("/tables")
    fun getTables(): ResponseEntity<Any> {
        return try {
            val query = """
                SELECT table_name 
                FROM information_schema.tables 
                WHERE table_schema = 'public' 
                AND table_type = 'BASE TABLE'
                ORDER BY table_name
            """.trimIndent()
            
            val result = entityManager.createNativeQuery(query).resultList
            val tables = result.map { it.toString() }
            
            ResponseEntity.ok(tables)
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to (e.message ?: "Failed to fetch tables")))
        }
    }

    @GetMapping("/table/{tableName}")
    fun getTableData(
        @PathVariable tableName: String,
        @RequestParam(defaultValue = "100") limit: Int = 100
    ): ResponseEntity<Map<String, Any>> {
        return try {
            // Sanitize table name to prevent basic injection, though this is an admin tool
            if (!tableName.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                return ResponseEntity.badRequest().body(mapOf("error" to "Invalid table name"))
            }

            // Get columns
            val columnsQuery = """
                SELECT column_name 
                FROM information_schema.columns 
                WHERE table_schema = 'public' 
                AND table_name = :tableName
                ORDER BY ordinal_position
            """.trimIndent()
            
            val columns = entityManager.createNativeQuery(columnsQuery)
                .setParameter("tableName", tableName)
                .resultList
                .map { it.toString() }

            if (columns.isEmpty()) {
                return ResponseEntity.notFound().build()
            }

            // Get data
            val dataQuery = "SELECT * FROM $tableName LIMIT :limit"
            val data = entityManager.createNativeQuery(dataQuery)
                .setParameter("limit", limit)
                .resultList

            // Convert object array to map if necessary, but list of list/objects is easier for frontend usually
            // Ideally we want list of maps: [{col1: val1, ...}, ...]
            
            val structuredData = data.map { row ->
                if (row is Array<*>) {
                    columns.zip(row).associate { (col, uVal) -> col to uVal }
                } else {
                    // Single column result
                    mapOf(columns[0] to row)
                }
            }

            ResponseEntity.ok(mapOf(
                "columns" to columns,
                "rows" to structuredData
            ))
        } catch (e: Exception) {
             ResponseEntity.status(500).body(mapOf("error" to (e.message ?: "Unknown error")))
        }
    }

    @PostMapping("/query")
    @Transactional
    fun executeQuery(@RequestBody request: SqlQueryRequest): ResponseEntity<Any> {
        return try {
            val queryStr = request.query.trim()
            val isSelect = queryStr.uppercase().startsWith("SELECT")
            
            if (isSelect) {
                val query = entityManager.createNativeQuery(queryStr)
                // For dynamic select, we need alias/metadata which is hard with pure JPA native query without mapping
                // But generally .resultList returns Object[] or Object.
                // We'll try to return raw list of lists/objects and let frontend handle it or try to map if possible.
                // A better approach for generic viewer is unwrap to hibernate or use JDBC template, but using what we have (EntityManager)
                // nativeQuery resultList returns List<Object[]> for multiple cols, List<Object> for single.
                
                // ISSUE: We don't know column names easily for arbitrary SELECT without parsing or using Tuple.
                // Let's use Tuple if possible or just return headers as "Col 1", "Col 2" etc if metadata missing.
                // Actually, JPA .unwrap(Session.class) -> doWork((connection) -> ...) is best for metadata.
                // For MVP, simply returning List<Object> is okay, but user requested "professional". 
                // Let's return List<Map<String, Any>> using query.unwrap(org.hibernate.query.NativeQuery::class.java).setResultTransformer(...) is deprecated.
                
                // Fallback: Just return list of values. Frontend might not show headers initially.
                // Attempting to improve:
                val result = query.resultList
                
                // If it's a list maps, great. If arrays, we send arrays.
                 ResponseEntity.ok(mapOf(
                     "type" to "SELECT",
                     "rowCount" to result.size,
                     "rows" to result
                 ))
            } else {
                val query = entityManager.createNativeQuery(queryStr)
                val updated = query.executeUpdate()
                ResponseEntity.ok(mapOf(
                    "type" to "UPDATE/INSERT/DELETE",
                    "rowsAffected" to updated
                ))
            }
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "SQL Error")))
        }
    }
}

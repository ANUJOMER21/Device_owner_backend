package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.JwtService
import com.da_emi_locker.backend.service.S3StorageService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val s3StorageService: S3StorageService
) {
    
    private val logger = LoggerFactory.getLogger(AdminController::class.java)
    
    @Value("\${admin.password:admin123}")
    private var adminPassword: String = "admin123"
    
    @Value("\${admin.password.hash:}")
    private var adminPasswordHash: String? = null
    
    init {
        logger.info("AdminController initialized")
        logger.info("Admin password configured: ${if (adminPasswordHash.isNullOrBlank()) "Plain text" else "Hashed"}")
    }
    
    data class AdminLoginRequestDto(
        @field:NotBlank(message = "Password is required")
        val password: String
    )
    
    data class AdminLoginResponse(
        val success: Boolean,
        val message: String,
        val data: AdminLoginData? = null,
        val token: String? = null,
        val adminId: String? = null
    )
    
    data class AdminLoginData(
        val adminId: String,
        val token: String
    )
    
    @PostMapping("/login")
    fun adminLogin(@Valid @RequestBody request: AdminLoginRequestDto): ResponseEntity<AdminLoginResponse> {
        logger.info("Admin login attempt received")
        
        try {
            // Check password
            val isValidPassword = if (adminPasswordHash.isNullOrBlank()) {
                // Use plain text comparison (for development)
                logger.info("Using plain text password comparison")
                logger.info("Configured password length: ${adminPassword.length}")
                logger.info("Received password length: ${request.password.length}")
                val matches = request.password == adminPassword
                logger.info("Password match: $matches")
                matches
            } else {
                // Use hashed password comparison (for production)
                logger.info("Using hashed password comparison")
                passwordEncoder.matches(request.password, adminPasswordHash!!)
            }
            
            if (!isValidPassword) {
                logger.warn("Admin login failed - Invalid password")
                return ResponseEntity.status(401).body(
                    AdminLoginResponse(
                        success = false,
                        message = "Invalid admin password"
                    )
                )
            }
            
            logger.info("Admin login successful - generating token")
            
            // Generate JWT token
            val adminId = "admin"
            val token = try {
                jwtService.generateToken(adminId, "admin@system.local", UUID.randomUUID().toString())
            } catch (e: Exception) {
                logger.error("Failed to generate JWT token", e)
                return ResponseEntity.status(500).body(
                    AdminLoginResponse(
                        success = false,
                        message = "Failed to generate authentication token: ${e.message}"
                    )
                )
            }
            
            logger.info("Token generated successfully, length: ${token.length}")
            
            return ResponseEntity.ok(
                AdminLoginResponse(
                    success = true,
                    message = "Login successful",
                    data = AdminLoginData(
                        adminId = adminId,
                        token = token
                    ),
                    token = token, // For backward compatibility
                    adminId = adminId
                )
            )
        } catch (e: Exception) {
            logger.error("Unexpected error during admin login", e)
            return ResponseEntity.status(500).body(
                AdminLoginResponse(
                    success = false,
                    message = "Internal server error: ${e.message}"
                )
            )
        }
    }
    
    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "status" to "ok",
            "service" to "admin",
            "timestamp" to Instant.now().toString()
        ))
    }

    /** Verify S3 configuration and bucket reachability. No auth required. */
    @GetMapping("/health/s3")
    fun s3Health(): ResponseEntity<Map<String, Any>> {
        val result = s3StorageService.verifyS3().toMutableMap()
        result["status"] = if (result["reachable"] == true) "ok" else "error"
        result["timestamp"] = Instant.now().toString()
        val statusCode = if (result["reachable"] == true) 200 else 503
        return ResponseEntity.status(statusCode).body(result)
    }
    
    @GetMapping("/firebase/status")
    fun firebaseStatus(): ResponseEntity<Map<String, Any>> {
        val apps = com.google.firebase.FirebaseApp.getApps()
        val isInitialized = apps.isNotEmpty()
        
        return ResponseEntity.ok(mapOf(
            "initialized" to isInitialized,
            "appCount" to apps.size,
            "apps" to apps.map { it.name },
            "fcmEnabled" to true
        ))
    }
}

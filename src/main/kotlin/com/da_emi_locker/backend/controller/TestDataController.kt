package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.entity.Dealer
import com.da_emi_locker.backend.entity.DealerStatus
import com.da_emi_locker.backend.repository.DealerRepository
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.time.Instant

/**
 * Test data controller - ONLY FOR DEVELOPMENT/TESTING
 * Active only in the 'dev' Spring profile.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/test")
class TestDataController(
    private val dealerRepository: DealerRepository,
    private val passwordEncoder: PasswordEncoder
) {
    
    @PostMapping("/create-test-dealer")
    fun createTestDealer(): ResponseEntity<Map<String, Any>> {
        // Check if dealer already exists
        val existingDealer = dealerRepository.findByEmail("dealer@test.com")
        
        if (existingDealer.isPresent) {
            // Update existing dealer with correct password hash
            val dealer = existingDealer.get()
            dealer.passwordHash = passwordEncoder.encode("password123") as String
            dealer.status = DealerStatus.active
            dealer.isPinSet = false
            dealer.pinHash = null
            dealer.updatedAt = Instant.now()
            dealerRepository.save(dealer)
            
            return ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Test dealer password updated",
                "dealerId" to dealer.dealerId,
                "email" to dealer.email,
                "password" to "password123"
            ))
        }
        
        // Create test dealer
        val hashedPassword = passwordEncoder.encode("password123") as String
        val dealer = Dealer().apply {
            dealerId = "DLR001"
            name = "Test Dealer"
            email = "dealer@test.com"
            passwordHash = hashedPassword
            status = DealerStatus.active
            isPinSet = false
            createdAt = Instant.now()
            updatedAt = Instant.now()
        }
        
        val savedDealer = dealerRepository.save(dealer)
        
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Test dealer created successfully",
            "dealerId" to savedDealer.dealerId,
            "email" to savedDealer.email,
            "password" to "password123",
            "note" to "Use this password for testing"
        ))
    }
    
    @GetMapping("/hash-password")
    fun hashPassword(@RequestParam password: String): ResponseEntity<Map<String, Any>> {
        val hash = passwordEncoder.encode(password) as String
        return ResponseEntity.ok(mapOf<String, Any>(
            "password" to password,
            "hash" to hash
        ))
    }
    
    @GetMapping("/check-dealer")
    fun checkDealer(@RequestParam email: String = "dealer@test.com"): ResponseEntity<Map<String, Any>> {
        val dealer = dealerRepository.findByEmail(email)
        
        if (!dealer.isPresent) {
            return ResponseEntity.ok(mapOf(
                "found" to false,
                "message" to "Dealer not found"
            ))
        }
        
        val d = dealer.get()
        val testPassword = "password123"
        val passwordMatches = passwordEncoder.matches(testPassword, d.passwordHash)
        
        return ResponseEntity.ok(mapOf(
            "found" to true,
            "dealerId" to d.dealerId,
            "email" to d.email,
            "name" to d.name,
            "status" to d.status.name,
            "isPinSet" to d.isPinSet,
            "passwordHash" to d.passwordHash,
            "passwordMatches" to passwordMatches,
            "testPassword" to testPassword
        ))
    }
}

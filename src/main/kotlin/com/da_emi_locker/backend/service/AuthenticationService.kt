package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.Dealer
import com.da_emi_locker.backend.entity.DealerStatus
import com.da_emi_locker.backend.repository.DealerRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AuthenticationService(
    private val dealerRepository: DealerRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder
) {
    
    data class LoginRequest(
        val email: String,
        val password: String
    )
    
    data class LoginResponse(
        val success: Boolean,
        val message: String,
        val data: LoginData? = null
    )
    
    data class LoginData(
        val userId: String,
        val name: String,
        val email: String,
        val mobile: String? = null,
        val token: String,
        val isPinSet: Boolean
    )
    
    data class PinRequest(
        val pin: String
    )
    
    data class PinResponse(
        val success: Boolean,
        val message: String,
        val token: String? = null
    )
    
    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val dealer = dealerRepository.findByEmail(request.email)
            .orElse(null) ?: return LoginResponse(
                success = false,
                message = "Invalid email or password"
            )
        
        // Check if dealer is active
        if (dealer.status != DealerStatus.active) {
            return LoginResponse(
                success = false,
                message = "Account is ${dealer.status.name}. Please contact support."
            )
        }
        
        // Verify password
        if (!passwordEncoder.matches(request.password, dealer.passwordHash)) {
            return LoginResponse(
                success = false,
                message = "Invalid email or password"
            )
        }
        
        // Single-device: block login on new device if already logged in elsewhere (use DB flag, not token)
        if (dealer.isLoggedIn) {
            return LoginResponse(
                success = false,
                message = "You are already logged in on another device. Please logout from that device first, or ask your admin to force logout from the admin panel."
            )
        }
        
        // Update last login, token id, and login flag (single-device: only this token is valid)
        dealer.lastLogin = Instant.now()
        val tokenId = UUID.randomUUID().toString()
        dealer.currentTokenId = tokenId
        dealer.isLoggedIn = true
        dealerRepository.save(dealer)
        
        // Generate JWT token with jti for single-device validation
        val token = jwtService.generateToken(dealer.dealerId, dealer.email, tokenId)
        
        return LoginResponse(
            success = true,
            message = "Login successful",
            data = LoginData(
                userId = dealer.dealerId,
                name = dealer.name,
                email = dealer.email,
                mobile = dealer.phone,
                token = token,
                isPinSet = dealer.isPinSet
            )
        )
    }
    
    @Transactional
    fun setPin(dealerId: String, request: PinRequest): PinResponse {
        val dealer = dealerRepository.findByDealerId(dealerId)
            .orElse(null) ?: return PinResponse(
                success = false,
                message = "Dealer not found"
            )
        
        // Validate PIN (should be 4-6 digits)
        if (!request.pin.matches(Regex("^\\d{4,6}$"))) {
            return PinResponse(
                success = false,
                message = "PIN must be 4-6 digits"
            )
        }
        
        // Hash and store PIN
        dealer.pinHash = passwordEncoder.encode(request.pin)
        dealer.isPinSet = true
        dealerRepository.save(dealer)
        
        return PinResponse(
            success = true,
            message = "PIN set successfully"
        )
    }
    
    @Transactional
    fun verifyPin(dealerId: String, request: PinRequest): PinResponse {
        val dealer = dealerRepository.findByDealerId(dealerId)
            .orElse(null) ?: return PinResponse(
                success = false,
                message = "Dealer not found"
            )
        
        // Check if PIN is set
        if (!dealer.isPinSet || dealer.pinHash == null) {
            return PinResponse(
                success = false,
                message = "PIN not set. Please set PIN first."
            )
        }
        
        // Verify PIN
        if (!passwordEncoder.matches(request.pin, dealer.pinHash!!)) {
            return PinResponse(
                success = false,
                message = "Invalid PIN"
            )
        }
        
        // Single-device: new token invalidates previous session
        val tokenId = UUID.randomUUID().toString()
        dealer.currentTokenId = tokenId
        dealer.isLoggedIn = true
        dealerRepository.save(dealer)
        
        // Generate new JWT token with jti
        val token = jwtService.generateToken(dealer.dealerId, dealer.email, tokenId)
        
        return PinResponse(
            success = true,
            message = "PIN verified successfully",
            token = token
        )
    }
    
    /** Clear current session so dealer can login on another device. */
    @Transactional
    fun logout(dealerId: String): Boolean {
        val dealer = dealerRepository.findByDealerId(dealerId).orElse(null) ?: return false
        dealer.currentTokenId = null
        dealer.isLoggedIn = false
        dealerRepository.save(dealer)
        return true
    }
}

package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.Dealer
import com.da_emi_locker.backend.entity.DealerStatus
import com.da_emi_locker.backend.entity.SalesExecutiveStatus
import com.da_emi_locker.backend.repository.DealerRepository
import com.da_emi_locker.backend.repository.SalesExecutiveRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AuthenticationService(
    private val dealerRepository: DealerRepository,
    private val salesExecutiveRepository: SalesExecutiveRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder
) {

    data class LoginRequest(
        val phone: String,
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
        val isPinSet: Boolean,
        /** "dealer" or "sales_executive" */
        val role: String = "dealer",
        /** For sales executives: the dealer they belong to */
        val dealerId: String? = null
    )

    data class PinRequest(
        val pin: String
    )

    data class PinResponse(
        val success: Boolean,
        val message: String,
        val token: String? = null
    )

    /**
     * Unified login: accepts phone + password.
     * First looks for a dealer by phone, then for a sales executive by phone.
     */
    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        // ---------- Try Dealer login ----------
        val dealer = dealerRepository.findByPhone(request.phone).orElse(null)
        if (dealer != null) {
            return loginAsDealer(dealer, request.password)
        }

        // ---------- Try Sales Executive login ----------
        val se = salesExecutiveRepository.findByPhone(request.phone).orElse(null)
        if (se != null) {
            return loginAsSalesExecutive(se, request.password)
        }

        return LoginResponse(success = false, message = "Invalid phone number or password")
    }

    private fun loginAsDealer(dealer: Dealer, password: String): LoginResponse {
        if (dealer.status != DealerStatus.active) {
            return LoginResponse(success = false, message = "Account is ${dealer.status.name}. Please contact support.")
        }
        if (!passwordEncoder.matches(password, dealer.passwordHash)) {
            return LoginResponse(success = false, message = "Invalid phone number or password")
        }
        if (dealer.isLoggedIn) {
            return LoginResponse(
                success = false,
                message = "You are already logged in on another device. Please logout from that device first, or ask your admin to force logout from the admin panel."
            )
        }

        dealer.lastLogin = Instant.now()
        val tokenId = UUID.randomUUID().toString()
        dealer.currentTokenId = tokenId
        dealer.isLoggedIn = true
        dealerRepository.save(dealer)

        val token = jwtService.generateToken(
            dealerId = dealer.dealerId,
            email = dealer.email,
            tokenId = tokenId,
            role = "dealer"
        )

        return LoginResponse(
            success = true,
            message = "Login successful",
            data = LoginData(
                userId = dealer.dealerId,
                name = dealer.name,
                email = dealer.email,
                mobile = dealer.phone,
                token = token,
                isPinSet = dealer.isPinSet,
                role = "dealer",
                dealerId = dealer.dealerId
            )
        )
    }

    private fun loginAsSalesExecutive(se: com.da_emi_locker.backend.entity.SalesExecutive, password: String): LoginResponse {
        if (se.status != SalesExecutiveStatus.active) {
            return LoginResponse(success = false, message = "Account is ${se.status.name}. Please contact your dealer.")
        }
        if (!passwordEncoder.matches(password, se.passwordHash)) {
            return LoginResponse(success = false, message = "Invalid phone number or password")
        }
        if (se.isLoggedIn) {
            return LoginResponse(
                success = false,
                message = "You are already logged in on another device. Please logout from that device first, or ask your dealer to force logout."
            )
        }

        se.lastLogin = Instant.now()
        val tokenId = UUID.randomUUID().toString()
        se.currentTokenId = tokenId
        se.isLoggedIn = true
        salesExecutiveRepository.save(se)

        // Use dealerId in the token so all dealer-scoped APIs still work,
        // but include the SE role and SE id in claims.
        val token = jwtService.generateToken(
            dealerId = se.dealerId,
            email = se.salesExecutiveId, // SE has no email, use SE id as secondary identifier
            tokenId = tokenId,
            role = "sales_executive",
            salesExecutiveId = se.salesExecutiveId
        )

        return LoginResponse(
            success = true,
            message = "Login successful",
            data = LoginData(
                userId = se.salesExecutiveId,
                name = se.name,
                email = "", // SE has no email
                mobile = se.phone,
                token = token,
                isPinSet = se.isPinSet,
                role = "sales_executive",
                dealerId = se.dealerId
            )
        )
    }

    @Transactional
    fun setPin(dealerId: String, request: PinRequest, salesExecutiveId: String? = null): PinResponse {
        if (!request.pin.matches(Regex("^\\d{4,6}$"))) {
            return PinResponse(success = false, message = "PIN must be 4-6 digits")
        }

        if (salesExecutiveId != null) {
            val se = salesExecutiveRepository.findBySalesExecutiveId(salesExecutiveId).orElse(null)
                ?: return PinResponse(success = false, message = "Sales executive not found")
            se.pinHash = passwordEncoder.encode(request.pin)
            se.isPinSet = true
            salesExecutiveRepository.save(se)
            return PinResponse(success = true, message = "PIN set successfully")
        }

        val dealer = dealerRepository.findByDealerId(dealerId).orElse(null)
            ?: return PinResponse(success = false, message = "Dealer not found")
        dealer.pinHash = passwordEncoder.encode(request.pin)
        dealer.isPinSet = true
        dealerRepository.save(dealer)
        return PinResponse(success = true, message = "PIN set successfully")
    }

    @Transactional
    fun verifyPin(dealerId: String, request: PinRequest, salesExecutiveId: String? = null): PinResponse {
        if (salesExecutiveId != null) {
            val se = salesExecutiveRepository.findBySalesExecutiveId(salesExecutiveId).orElse(null)
                ?: return PinResponse(success = false, message = "Sales executive not found")
            if (!se.isPinSet || se.pinHash == null) {
                return PinResponse(success = false, message = "PIN not set. Please set PIN first.")
            }
            if (!passwordEncoder.matches(request.pin, se.pinHash!!)) {
                return PinResponse(success = false, message = "Invalid PIN")
            }
            val tokenId = UUID.randomUUID().toString()
            se.currentTokenId = tokenId
            se.isLoggedIn = true
            salesExecutiveRepository.save(se)
            val token = jwtService.generateToken(
                dealerId = se.dealerId,
                email = se.salesExecutiveId,
                tokenId = tokenId,
                role = "sales_executive",
                salesExecutiveId = se.salesExecutiveId
            )
            return PinResponse(success = true, message = "PIN verified successfully", token = token)
        }

        val dealer = dealerRepository.findByDealerId(dealerId).orElse(null)
            ?: return PinResponse(success = false, message = "Dealer not found")
        if (!dealer.isPinSet || dealer.pinHash == null) {
            return PinResponse(success = false, message = "PIN not set. Please set PIN first.")
        }
        if (!passwordEncoder.matches(request.pin, dealer.pinHash!!)) {
            return PinResponse(success = false, message = "Invalid PIN")
        }
        val tokenId = UUID.randomUUID().toString()
        dealer.currentTokenId = tokenId
        dealer.isLoggedIn = true
        dealerRepository.save(dealer)
        val token = jwtService.generateToken(dealer.dealerId, dealer.email, tokenId, role = "dealer")
        return PinResponse(success = true, message = "PIN verified successfully", token = token)
    }

    /** Clear current session so dealer/SE can login on another device. */
    @Transactional
    fun logout(dealerId: String, salesExecutiveId: String? = null): Boolean {
        if (salesExecutiveId != null) {
            val se = salesExecutiveRepository.findBySalesExecutiveId(salesExecutiveId).orElse(null) ?: return false
            se.currentTokenId = null
            se.isLoggedIn = false
            salesExecutiveRepository.save(se)
            return true
        }
        val dealer = dealerRepository.findByDealerId(dealerId).orElse(null) ?: return false
        dealer.currentTokenId = null
        dealer.isLoggedIn = false
        dealerRepository.save(dealer)
        return true
    }
}

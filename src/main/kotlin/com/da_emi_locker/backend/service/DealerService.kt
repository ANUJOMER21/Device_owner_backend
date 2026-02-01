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
class DealerService(
    private val dealerRepository: DealerRepository,
    private val passwordEncoder: PasswordEncoder
) {
    
    data class RegisterDealerRequest(
        val name: String,
        val email: String,
        val password: String,
        val gstNumber: String,
        val phone: String? = null,
        val address: String? = null,
        val city: String? = null,
        val state: String? = null,
        val pincode: String? = null,
        val businessName: String? = null
    )
    
    data class RegisterDealerResponse(
        val success: Boolean,
        val message: String,
        val dealerId: String? = null,
        val email: String? = null
    )
    
    data class SetCustomerLimitRequest(
        val customerLimit: Int
    )
    
    data class SetCustomerLimitResponse(
        val success: Boolean,
        val message: String,
        val dealerId: String? = null,
        val customerLimit: Int? = null
    )
    
    @Transactional
    fun registerDealer(request: RegisterDealerRequest): RegisterDealerResponse {
        // Validate email format
        if (!request.email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))) {
            return RegisterDealerResponse(
                success = false,
                message = "Invalid email format"
            )
        }
        
        // Check if email already exists
        if (dealerRepository.existsByEmail(request.email)) {
            return RegisterDealerResponse(
                success = false,
                message = "Email already registered"
            )
        }
        
        // Validate GST number format (15 characters, alphanumeric)
        val gstNumber = request.gstNumber.uppercase().trim().replace("\\s".toRegex(), "")
        if (gstNumber.length != 15) {
            return RegisterDealerResponse(
                success = false,
                message = "GST number must be exactly 15 characters"
            )
        }
        if (!gstNumber.matches(Regex("^[0-9A-Z]{15}$"))) {
            return RegisterDealerResponse(
                success = false,
                message = "GST number must contain only alphanumeric characters"
            )
        }
        
        // Check if GST number already exists
        if (dealerRepository.existsByGstNumber(gstNumber)) {
            return RegisterDealerResponse(
                success = false,
                message = "GST number already registered"
            )
        }
        
        // Validate password strength (minimum 6 characters)
        if (request.password.length < 6) {
            return RegisterDealerResponse(
                success = false,
                message = "Password must be at least 6 characters long"
            )
        }
        
        // Generate unique dealer ID using GST number
        val dealerId = generateDealerId(gstNumber)
        
        // Create new dealer
        val dealer = Dealer().apply {
            this.dealerId = dealerId
            this.name = request.name
            this.email = request.email
            this.passwordHash = passwordEncoder.encode(request.password) as String
            this.plainPassword = request.password  // Store plain password for admin viewing
            this.gstNumber = gstNumber
            this.phone = request.phone
            this.address = request.address
            this.city = request.city
            this.state = request.state
            this.pincode = request.pincode
            this.businessName = request.businessName
            this.status = DealerStatus.active
            this.isPinSet = false
            this.customerLimit = 0 // Default to 0, must be set by admin
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }
        
        val savedDealer = dealerRepository.save(dealer)
        
        return RegisterDealerResponse(
            success = true,
            message = "Dealer registered successfully",
            dealerId = savedDealer.dealerId,
            email = savedDealer.email
        )
    }
    
    @Transactional
    fun setCustomerLimit(dealerId: String, request: SetCustomerLimitRequest): SetCustomerLimitResponse {
        val dealer = dealerRepository.findByDealerId(dealerId)
            .orElse(null) ?: return SetCustomerLimitResponse(
                success = false,
                message = "Dealer not found"
            )
        
        // Validate limit (must be non-negative)
        if (request.customerLimit < 0) {
            return SetCustomerLimitResponse(
                success = false,
                message = "Customer limit must be 0 or greater"
            )
        }
        
        dealer.customerLimit = request.customerLimit
        dealer.updatedAt = Instant.now()
        dealerRepository.save(dealer)
        
        return SetCustomerLimitResponse(
            success = true,
            message = "Customer limit updated successfully",
            dealerId = dealer.dealerId,
            customerLimit = dealer.customerLimit
        )
    }
    
    fun getDealerInfo(dealerId: String): Dealer? {
        return dealerRepository.findByDealerId(dealerId).orElse(null)
    }
    
    fun getAllDealers(): List<Dealer> {
        return dealerRepository.findAll()
    }
    
    data class UpdateDealerRequest(
        val name: String? = null,
        val email: String? = null,
        val phone: String? = null,
        val address: String? = null,
        val status: String? = null,
        val customerLimit: Int? = null,
        val gstNumber: String? = null,
        val city: String? = null,
        val state: String? = null,
        val pincode: String? = null,
        val businessName: String? = null
    )
    
    data class UpdateDealerResponse(
        val success: Boolean,
        val message: String,
        val dealer: DealerData? = null
    )
    
    data class DealerData(
        val dealerId: String,
        val name: String,
        val email: String,
        val phone: String?,
        val address: String?,
        val status: String,
        val customerLimit: Int,
        val gstNumber: String?,
        val createdAt: String,
        val updatedAt: String,
        val password: String? = null  // Plain password for admin viewing
    )
    
    @Transactional
    fun updateDealer(dealerId: String, request: UpdateDealerRequest): UpdateDealerResponse {
        val dealer = dealerRepository.findByDealerId(dealerId)
            .orElse(null) ?: return UpdateDealerResponse(
                success = false,
                message = "Dealer not found"
            )
        
        // Update fields
        request.name?.let { dealer.name = it }
        request.phone?.let { dealer.phone = it }
        request.address?.let { dealer.address = it }
        request.city?.let { dealer.city = it }
        request.state?.let { dealer.state = it }
        request.pincode?.let { dealer.pincode = it }
        request.businessName?.let { dealer.businessName = it }
        
        // Update email if provided and different
        if (request.email != null && request.email != dealer.email) {
            if (dealerRepository.existsByEmail(request.email)) {
                return UpdateDealerResponse(
                    success = false,
                    message = "Email already registered"
                )
            }
            dealer.email = request.email
        }
        
        // Update status if provided
        if (request.status != null) {
            try {
                val newStatus = DealerStatus.valueOf(request.status.lowercase())
                dealer.status = newStatus
                // When suspending or deactivating, clear current session so app gets 401 and logs out
                if (newStatus == DealerStatus.suspended || newStatus == DealerStatus.inactive) {
                    dealer.currentTokenId = null
                    dealer.isLoggedIn = false
                }
            } catch (e: IllegalArgumentException) {
                return UpdateDealerResponse(
                    success = false,
                    message = "Invalid status. Must be: active, inactive, or suspended"
                )
            }
        }
        
        // Update customer limit if provided
        request.customerLimit?.let {
            if (it < 0) {
                return UpdateDealerResponse(
                    success = false,
                    message = "Customer limit must be 0 or greater"
                )
            }
            dealer.customerLimit = it
        }
        
        // Update GST number if provided
        if (request.gstNumber != null && request.gstNumber != dealer.gstNumber) {
            val gstNumber = request.gstNumber.uppercase().trim().replace("\\s".toRegex(), "")
            if (gstNumber.length != 15) {
                return UpdateDealerResponse(
                    success = false,
                    message = "GST number must be exactly 15 characters"
                )
            }
            if (dealerRepository.existsByGstNumber(gstNumber)) {
                return UpdateDealerResponse(
                    success = false,
                    message = "GST number already registered"
                )
            }
            dealer.gstNumber = gstNumber
        }
        
        dealer.updatedAt = Instant.now()
        val savedDealer = dealerRepository.save(dealer)
        
        return UpdateDealerResponse(
            success = true,
            message = "Dealer updated successfully",
            dealer = DealerData(
                dealerId = savedDealer.dealerId,
                name = savedDealer.name,
                email = savedDealer.email,
                phone = savedDealer.phone,
                address = savedDealer.address,
                status = savedDealer.status.name,
                customerLimit = savedDealer.customerLimit,
                gstNumber = savedDealer.gstNumber,
                createdAt = savedDealer.createdAt?.toString() ?: "",
                updatedAt = savedDealer.updatedAt?.toString() ?: "",
                password = savedDealer.plainPassword
            )
        )
    }
    
    @Transactional
    fun deleteDealer(dealerId: String): UpdateDealerResponse {
        val dealer = dealerRepository.findByDealerId(dealerId)
            .orElse(null) ?: return UpdateDealerResponse(
                success = false,
                message = "Dealer not found"
            )
        
        dealerRepository.delete(dealer)
        
        return UpdateDealerResponse(
            success = true,
            message = "Dealer deleted successfully"
        )
    }
    
    @Transactional
    fun updateFcmToken(dealerId: String, fcmToken: String?): UpdateDealerResponse {
        val dealer = dealerRepository.findByDealerId(dealerId)
            .orElse(null) ?: return UpdateDealerResponse(
                success = false,
                message = "Dealer not found"
            )
        
        dealer.fcmToken = fcmToken
        dealer.updatedAt = Instant.now()
        dealerRepository.save(dealer)
        
        return UpdateDealerResponse(
            success = true,
            message = "FCM token updated successfully"
        )
    }
    
    private fun generateDealerId(gstNumber: String): String {
        // Generate dealer ID using GST number: DLR + last 6 digits of GST + timestamp suffix
        // GST format: 22AAAAA0000A1Z5, we'll use last 6 chars (A1Z5) + timestamp
        val gstSuffix = gstNumber.takeLast(6).uppercase()
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        return "DLR$gstSuffix$timestamp"
    }
}

package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.Activity
import com.da_emi_locker.backend.entity.Dealer
import com.da_emi_locker.backend.repository.ActivityRepository
import com.da_emi_locker.backend.repository.DealerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ProfileService(
    private val dealerRepository: DealerRepository,
    private val activityRepository: ActivityRepository
) {
    
    data class UpdateProfileRequest(
        val name: String? = null,
        val email: String? = null,
        val phone: String? = null,
        val address: String? = null,
        val city: String? = null,
        val state: String? = null,
        val pincode: String? = null,
        val businessName: String? = null,
        val gstNumber: String? = null,
        val profileImageUrl: String? = null
    )
    
    data class ProfileResponse(
        val success: Boolean,
        val message: String,
        val profile: ProfileData? = null
    )
    
    data class ProfileData(
        val dealerId: String,
        val name: String,
        val email: String,
        val phone: String?,
        val address: String?,
        val city: String?,
        val state: String?,
        val pincode: String?,
        val businessName: String?,
        val gstNumber: String?,
        val profileImageUrl: String?,
        val status: String,
        val customerLimit: Int,
        val createdAt: String,
        val updatedAt: String
    )
    
    fun getProfile(dealerId: String): ProfileResponse {
        val dealer = dealerRepository.findByDealerId(dealerId)
            .orElse(null) ?: return ProfileResponse(
                success = false,
                message = "Dealer not found"
            )
        
        return ProfileResponse(
            success = true,
            message = "Profile retrieved successfully",
            profile = ProfileData(
                dealerId = dealer.dealerId,
                name = dealer.name,
                email = dealer.email,
                phone = dealer.phone,
                address = dealer.address,
                city = dealer.city,
                state = dealer.state,
                pincode = dealer.pincode,
                businessName = dealer.businessName,
                gstNumber = dealer.gstNumber,
                profileImageUrl = dealer.profileImageUrl,
                status = dealer.status.name,
                customerLimit = dealer.customerLimit,
                createdAt = dealer.createdAt?.toString() ?: "",
                updatedAt = dealer.updatedAt?.toString() ?: ""
            )
        )
    }
    
    @Transactional
    fun updateProfile(dealerId: String, request: UpdateProfileRequest): ProfileResponse {
        val dealer = dealerRepository.findByDealerId(dealerId)
            .orElse(null) ?: return ProfileResponse(
                success = false,
                message = "Dealer not found"
            )
        
        // Validate email if provided
        if (request.email != null && !request.email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))) {
            return ProfileResponse(
                success = false,
                message = "Invalid email format"
            )
        }
        
        // Check email uniqueness if changing email
        if (request.email != null && request.email != dealer.email) {
            if (dealerRepository.existsByEmail(request.email)) {
                return ProfileResponse(
                    success = false,
                    message = "Email already registered"
                )
            }
        }
        
        // Check GST number uniqueness if changing GST
        if (request.gstNumber != null && request.gstNumber != dealer.gstNumber) {
            if (dealerRepository.existsByGstNumber(request.gstNumber)) {
                return ProfileResponse(
                    success = false,
                    message = "GST number already registered"
                )
            }
        }
        
        // Update fields
        request.name?.let { dealer.name = it }
        request.email?.let { dealer.email = it }
        request.phone?.let { dealer.phone = it }
        request.address?.let { dealer.address = it }
        request.city?.let { dealer.city = it }
        request.state?.let { dealer.state = it }
        request.pincode?.let { dealer.pincode = it }
        request.businessName?.let { dealer.businessName = it }
        request.gstNumber?.let { dealer.gstNumber = it }
        request.profileImageUrl?.let { dealer.profileImageUrl = it }
        
        dealer.updatedAt = Instant.now()
        dealerRepository.save(dealer)
        
        // Log activity (using metadata to store dealer_id since activities table doesn't have dealer_id column)
        val activity = Activity().apply {
            this.customerId = null
            this.deviceId = null
            this.activityType = "profile_updated"
            this.activityDescription = "Dealer profile updated for ${dealer.name}"
            this.metadata = "{\"dealer_id\":\"$dealerId\"}"
            this.createdAt = Instant.now()
        }
        activityRepository.save(activity)
        
        return ProfileResponse(
            success = true,
            message = "Profile updated successfully",
            profile = ProfileData(
                dealerId = dealer.dealerId,
                name = dealer.name,
                email = dealer.email,
                phone = dealer.phone,
                address = dealer.address,
                city = dealer.city,
                state = dealer.state,
                pincode = dealer.pincode,
                businessName = dealer.businessName,
                gstNumber = dealer.gstNumber,
                profileImageUrl = dealer.profileImageUrl,
                status = dealer.status.name,
                customerLimit = dealer.customerLimit,
                createdAt = dealer.createdAt?.toString() ?: "",
                updatedAt = dealer.updatedAt?.toString() ?: ""
            )
        )
    }
}

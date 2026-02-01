package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.Activity
import com.da_emi_locker.backend.entity.AadharDetails
import com.da_emi_locker.backend.entity.Customer
import com.da_emi_locker.backend.repository.ActivityRepository
import com.da_emi_locker.backend.repository.AadharDetailsRepository
import com.da_emi_locker.backend.repository.CustomerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
class AadharService(
    private val aadharDetailsRepository: AadharDetailsRepository,
    private val customerRepository: CustomerRepository,
    private val activityRepository: ActivityRepository
) {
    
    data class AadharRequest(
        val aadharNumber: String,
        val fullName: String,
        val dateOfBirth: String? = null,
        val address: String? = null,
        val frontImageUrl: String? = null,
        val backImageUrl: String? = null
    )
    
    data class AadharResponse(
        val success: Boolean,
        val message: String,
        val aadharDetails: AadharData? = null
    )
    
    data class AadharData(
        val customerId: String,
        val aadharNumber: String,
        val fullName: String,
        val dateOfBirth: String?,
        val address: String?,
        val frontImageUrl: String?,
        val backImageUrl: String?,
        val verified: Boolean,
        val verifiedAt: String?,
        val createdAt: String,
        val updatedAt: String
    )
    
    @Transactional
    fun saveAadharDetails(dealerId: String, customerId: String, request: AadharRequest): AadharResponse {
        // Verify customer belongs to dealer
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return AadharResponse(
                success = false,
                message = "Customer not found"
            )
        
        if (customer.dealerId != dealerId) {
            return AadharResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        // Validate Aadhar number (12 digits)
        val aadharNumber = request.aadharNumber.trim()
        if (!aadharNumber.matches(Regex("^[0-9]{12}$"))) {
            return AadharResponse(
                success = false,
                message = "Invalid Aadhar number. Must be 12 digits."
            )
        }
        
        // Check if Aadhar number already exists for another customer
        val existingAadhar = aadharDetailsRepository.findByCustomerId(customerId)
        if (existingAadhar.map { it.aadharNumber != aadharNumber }.orElse(false)) {
            if (aadharDetailsRepository.existsByAadharNumber(aadharNumber)) {
                return AadharResponse(
                    success = false,
                    message = "Aadhar number already registered for another customer"
                )
            }
        }
        
        // Parse date of birth
        val dateOfBirth = request.dateOfBirth?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                return AadharResponse(
                    success = false,
                    message = "Invalid date format. Use YYYY-MM-DD"
                )
            }
        }
        
        // Get or create Aadhar details
        val aadharDetails = aadharDetailsRepository.findByCustomerId(customerId)
            .orElseGet {
                AadharDetails().apply {
                    this.customerId = customerId
                    this.createdAt = Instant.now()
                    this.updatedAt = Instant.now()
                }
            }
        
        aadharDetails.aadharNumber = aadharNumber
        aadharDetails.fullName = request.fullName
        aadharDetails.dateOfBirth = dateOfBirth
        aadharDetails.address = request.address
        request.frontImageUrl?.let { aadharDetails.frontImageUrl = it }
        request.backImageUrl?.let { aadharDetails.backImageUrl = it }
        aadharDetails.updatedAt = Instant.now()
        
        val savedAadhar = aadharDetailsRepository.save(aadharDetails)
        
        // Update customer status
        customer.aadharStatus = if (savedAadhar.frontImageUrl != null && savedAadhar.backImageUrl != null) {
            "completed"
        } else {
            "pending"
        }
        customer.updatedAt = Instant.now()
        customerRepository.save(customer)
        
        // Log activity
        val activity = Activity().apply {
            this.customerId = customerId
            this.activityType = "aadhar_updated"
            this.activityDescription = "Aadhar details updated for customer ${customer.name}"
            this.createdAt = Instant.now()
        }
        activityRepository.save(activity)
        
        return AadharResponse(
            success = true,
            message = "Aadhar details saved successfully",
            aadharDetails = AadharData(
                customerId = savedAadhar.customerId,
                aadharNumber = savedAadhar.aadharNumber,
                fullName = savedAadhar.fullName,
                dateOfBirth = savedAadhar.dateOfBirth?.toString(),
                address = savedAadhar.address,
                frontImageUrl = savedAadhar.frontImageUrl,
                backImageUrl = savedAadhar.backImageUrl,
                verified = savedAadhar.verified,
                verifiedAt = savedAadhar.verifiedAt?.toString(),
                createdAt = savedAadhar.createdAt?.toString() ?: "",
                updatedAt = savedAadhar.updatedAt?.toString() ?: ""
            )
        )
    }
    
    fun getAadharDetails(customerId: String, dealerId: String): AadharResponse {
        // Verify customer belongs to dealer
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return AadharResponse(
                success = false,
                message = "Customer not found"
            )
        
        if (customer.dealerId != dealerId) {
            return AadharResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        val aadharDetails = aadharDetailsRepository.findByCustomerId(customerId)
            .orElse(null) ?: return AadharResponse(
                success = false,
                message = "Aadhar details not found"
            )
        
        return AadharResponse(
            success = true,
            message = "Aadhar details retrieved successfully",
            aadharDetails = AadharData(
                customerId = aadharDetails.customerId,
                aadharNumber = aadharDetails.aadharNumber,
                fullName = aadharDetails.fullName,
                dateOfBirth = aadharDetails.dateOfBirth?.toString(),
                address = aadharDetails.address,
                frontImageUrl = aadharDetails.frontImageUrl,
                backImageUrl = aadharDetails.backImageUrl,
                verified = aadharDetails.verified,
                verifiedAt = aadharDetails.verifiedAt?.toString(),
                createdAt = aadharDetails.createdAt?.toString() ?: "",
                updatedAt = aadharDetails.updatedAt?.toString() ?: ""
            )
        )
    }
}

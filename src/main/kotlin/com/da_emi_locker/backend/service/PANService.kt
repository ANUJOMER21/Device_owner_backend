package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.Activity
import com.da_emi_locker.backend.entity.PANDetails
import com.da_emi_locker.backend.entity.Customer
import com.da_emi_locker.backend.repository.ActivityRepository
import com.da_emi_locker.backend.repository.PANDetailsRepository
import com.da_emi_locker.backend.repository.CustomerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
class PANService(
    private val panDetailsRepository: PANDetailsRepository,
    private val customerRepository: CustomerRepository,
    private val activityRepository: ActivityRepository
) {
    
    data class PANRequest(
        val panNumber: String,
        val fullName: String,
        val dateOfBirth: String? = null,
        val fatherName: String? = null,
        val imageUrl: String? = null
    )
    
    data class PANResponse(
        val success: Boolean,
        val message: String,
        val panDetails: PANData? = null
    )
    
    data class PANData(
        val customerId: String,
        val panNumber: String,
        val fullName: String,
        val dateOfBirth: String?,
        val fatherName: String?,
        val imageUrl: String?,
        val verified: Boolean,
        val verifiedAt: String?,
        val createdAt: String,
        val updatedAt: String
    )
    
    @Transactional
    fun savePANDetails(dealerId: String, customerId: String, request: PANRequest): PANResponse {
        // Verify customer belongs to dealer
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return PANResponse(
                success = false,
                message = "Customer not found"
            )
        
        if (customer.dealerId != dealerId) {
            return PANResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        // Validate PAN number (10 characters, alphanumeric)
        val panNumber = request.panNumber.trim().uppercase()
        if (!panNumber.matches(Regex("^[A-Z]{5}[0-9]{4}[A-Z]{1}$"))) {
            return PANResponse(
                success = false,
                message = "Invalid PAN number format. Must be in format: ABCDE1234F"
            )
        }
        
        // Check if PAN number already exists for another customer
        val existingPAN = panDetailsRepository.findByCustomerId(customerId)
        if (existingPAN.map { it.panNumber != panNumber }.orElse(false)) {
            if (panDetailsRepository.existsByPanNumber(panNumber)) {
                return PANResponse(
                    success = false,
                    message = "PAN number already registered for another customer"
                )
            }
        }
        
        // Parse date of birth
        val dateOfBirth = request.dateOfBirth?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                return PANResponse(
                    success = false,
                    message = "Invalid date format. Use YYYY-MM-DD"
                )
            }
        }
        
        // Get or create PAN details
        val panDetails = panDetailsRepository.findByCustomerId(customerId)
            .orElseGet {
                PANDetails().apply {
                    this.customerId = customerId
                    this.createdAt = Instant.now()
                    this.updatedAt = Instant.now()
                }
            }
        
        panDetails.panNumber = panNumber
        panDetails.fullName = request.fullName
        panDetails.dateOfBirth = dateOfBirth
        panDetails.fatherName = request.fatherName
        request.imageUrl?.let { panDetails.imageUrl = it }
        panDetails.updatedAt = Instant.now()
        
        val savedPAN = panDetailsRepository.save(panDetails)
        
        // Update customer status
        customer.panStatus = if (savedPAN.imageUrl != null) {
            "completed"
        } else {
            "pending"
        }
        customer.updatedAt = Instant.now()
        customerRepository.save(customer)
        
        // Log activity
        val activity = Activity().apply {
            this.customerId = customerId
            this.activityType = "pan_updated"
            this.activityDescription = "PAN details updated for customer ${customer.name}"
            this.createdAt = Instant.now()
        }
        activityRepository.save(activity)
        
        return PANResponse(
            success = true,
            message = "PAN details saved successfully",
            panDetails = PANData(
                customerId = savedPAN.customerId,
                panNumber = savedPAN.panNumber,
                fullName = savedPAN.fullName,
                dateOfBirth = savedPAN.dateOfBirth?.toString(),
                fatherName = savedPAN.fatherName,
                imageUrl = savedPAN.imageUrl,
                verified = savedPAN.verified,
                verifiedAt = savedPAN.verifiedAt?.toString(),
                createdAt = savedPAN.createdAt?.toString() ?: "",
                updatedAt = savedPAN.updatedAt?.toString() ?: ""
            )
        )
    }
    
    fun getPANDetails(customerId: String, dealerId: String): PANResponse {
        // Verify customer belongs to dealer
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return PANResponse(
                success = false,
                message = "Customer not found"
            )
        
        if (customer.dealerId != dealerId) {
            return PANResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        val panDetails = panDetailsRepository.findByCustomerId(customerId)
            .orElse(null) ?: return PANResponse(
                success = false,
                message = "PAN details not found"
            )
        
        return PANResponse(
            success = true,
            message = "PAN details retrieved successfully",
            panDetails = PANData(
                customerId = panDetails.customerId,
                panNumber = panDetails.panNumber,
                fullName = panDetails.fullName,
                dateOfBirth = panDetails.dateOfBirth?.toString(),
                fatherName = panDetails.fatherName,
                imageUrl = panDetails.imageUrl,
                verified = panDetails.verified,
                verifiedAt = panDetails.verifiedAt?.toString(),
                createdAt = panDetails.createdAt?.toString() ?: "",
                updatedAt = panDetails.updatedAt?.toString() ?: ""
            )
        )
    }
}

package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.SimDetails
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.SimDetailsRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class SimDetailsService(
    private val simDetailsRepository: SimDetailsRepository,
    private val customerRepository: CustomerRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(SimDetailsService::class.java)

    data class SaveSimDetailsRequest(
        val customerId: String,
        val deviceId: String? = null,
        val simData: String? = null
    )

    data class SaveSimDetailsResponse(
        val success: Boolean,
        val message: String
    )

    /**
     * Save SIM details from device. Only saves a new record if the phone number
     * (msisdn) has changed compared to the latest record. This keeps history of
     * SIM changes while avoiding unnecessary DB writes every 10 minutes.
     */
    fun saveFromDevice(request: SaveSimDetailsRequest): SaveSimDetailsResponse {
        // Verify customer exists
        if (!customerRepository.findByCustomerId(request.customerId).isPresent) {
            return SaveSimDetailsResponse(success = false, message = "Customer not found")
        }

        // Extract phone number from simData JSON
        val newPhoneNumber = extractPhoneNumber(request.simData)

        // Get latest SIM details for this customer
        val latest = simDetailsRepository.findTopByCustomerIdOrderByCreatedAtDesc(request.customerId)

        // Only save if phone number changed or no previous record exists
        if (latest != null) {
            val existingPhoneNumber = latest.phoneNumber ?: extractPhoneNumber(latest.simData)
            if (existingPhoneNumber == newPhoneNumber && newPhoneNumber != null) {
                // Phone number hasn't changed - just update the simData on existing record
                latest.simData = request.simData
                latest.updatedAt = java.time.Instant.now()
                simDetailsRepository.save(latest)
                return SaveSimDetailsResponse(success = true, message = "SIM details updated (no number change)")
            }
        }

        // Phone number changed or first record - save new entry for history
        val entity = SimDetails().apply {
            customerId = request.customerId
            deviceId = request.deviceId
            simData = request.simData
            phoneNumber = newPhoneNumber
            createdAt = java.time.Instant.now()
            updatedAt = java.time.Instant.now()
        }
        simDetailsRepository.save(entity)
        
        val message = if (latest == null) "SIM details saved" else "SIM change detected - new record saved"
        logger.info("SIM details for customer ${request.customerId}: $message (phone: $newPhoneNumber)")
        return SaveSimDetailsResponse(success = true, message = message)
    }

    /** Extract phone number (msisdn) from SIM data JSON */
    private fun extractPhoneNumber(simData: String?): String? {
        if (simData.isNullOrBlank()) return null
        return try {
            val node = objectMapper.readTree(simData)
            node.path("msisdn")
                .takeIf { !it.isMissingNode && !it.isNull }
                ?.asText()
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun getLatestByCustomerId(customerId: String): SimDetails? =
        simDetailsRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId)

    /** Get SIM change history (all records ordered by newest first) */
    fun getHistoryByCustomerId(customerId: String, limit: Int = 50): List<SimDetails> =
        simDetailsRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(0, limit))

    /** Returns true if customer belongs to dealer (for access check). */
    fun customerBelongsToDealer(customerId: String, dealerId: String): Boolean {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null) ?: return false
        return customer.dealerId == dealerId
    }

    /** For dealer: returns latest SIM details only if customer belongs to dealer. */
    fun getLatestForDealer(customerId: String, dealerId: String): SimDetails? {
        if (!customerBelongsToDealer(customerId, dealerId)) return null
        return simDetailsRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId)
    }

    /** For dealer: returns SIM change history only if customer belongs to dealer. */
    fun getHistoryForDealer(customerId: String, dealerId: String, limit: Int = 50): List<SimDetails>? {
        if (!customerBelongsToDealer(customerId, dealerId)) return null
        return simDetailsRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(0, limit))
    }
}

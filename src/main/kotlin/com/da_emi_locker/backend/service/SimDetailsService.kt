package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.SimDetails
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.SimDetailsRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class SimDetailsService(
    private val simDetailsRepository: SimDetailsRepository,
    private val customerRepository: CustomerRepository
) {

    data class SaveSimDetailsRequest(
        val customerId: String,
        val deviceId: String? = null,
        val simData: String? = null
    )

    data class SaveSimDetailsResponse(
        val success: Boolean,
        val message: String
    )

    fun saveFromDevice(request: SaveSimDetailsRequest): SaveSimDetailsResponse {
        val customer = customerRepository.findByCustomerId(request.customerId).orElse(null)
            ?: return SaveSimDetailsResponse(success = false, message = "Customer not found")
        val entity = SimDetails().apply {
            customerId = request.customerId
            deviceId = request.deviceId
            simData = request.simData
        }
        simDetailsRepository.save(entity)
        return SaveSimDetailsResponse(success = true, message = "SIM details saved")
    }

    fun getLatestByCustomerId(customerId: String): SimDetails? =
        simDetailsRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId)

    fun getHistoryByCustomerId(customerId: String, limit: Int = 10): List<SimDetails> =
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
}

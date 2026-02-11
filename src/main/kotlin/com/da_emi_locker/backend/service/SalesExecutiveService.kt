package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.SalesExecutive
import com.da_emi_locker.backend.entity.SalesExecutiveStatus
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DealerRepository
import com.da_emi_locker.backend.repository.SalesExecutiveRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SalesExecutiveService(
    private val salesExecutiveRepository: SalesExecutiveRepository,
    private val dealerRepository: DealerRepository,
    private val customerRepository: CustomerRepository,
    private val passwordEncoder: PasswordEncoder
) {

    // ---------- DTOs ----------

    data class CreateSalesExecutiveRequest(
        val name: String,
        val phone: String,
        val password: String,
        val assignedKits: Int = 0
    )

    data class UpdateSalesExecutiveRequest(
        val name: String? = null,
        val phone: String? = null,
        val assignedKits: Int? = null,
        val status: String? = null
    )

    data class SalesExecutiveData(
        val salesExecutiveId: String,
        val dealerId: String,
        val name: String,
        val phone: String,
        val status: String,
        val assignedKits: Int,
        val usedKits: Int,
        val availableKits: Int,
        val isPinSet: Boolean,
        val isLoggedIn: Boolean,
        val lastLogin: String?,
        val createdAt: String,
        val updatedAt: String,
        val password: String? = null
    )

    data class SalesExecutiveResponse(
        val success: Boolean,
        val message: String,
        val salesExecutive: SalesExecutiveData? = null
    )

    data class SalesExecutiveListResponse(
        val success: Boolean,
        val message: String,
        val salesExecutives: List<SalesExecutiveData>? = null,
        val totalAssignedKits: Int? = null,
        val dealerTotalKits: Int? = null,
        val dealerRemainingKits: Int? = null
    )

    // ---------- CRUD ----------

    @Transactional
    fun createSalesExecutive(dealerId: String, request: CreateSalesExecutiveRequest): SalesExecutiveResponse {
        val dealer = dealerRepository.findByDealerId(dealerId).orElse(null)
            ?: return SalesExecutiveResponse(success = false, message = "Dealer not found")

        // Validate phone
        if (!request.phone.matches(Regex("^[0-9]{10}$"))) {
            return SalesExecutiveResponse(success = false, message = "Phone number must be exactly 10 digits")
        }

        // Check duplicate phone under this dealer
        if (salesExecutiveRepository.existsByDealerIdAndPhone(dealerId, request.phone)) {
            return SalesExecutiveResponse(success = false, message = "A sales executive with this phone number already exists")
        }

        // Validate password
        if (request.password.length < 6) {
            return SalesExecutiveResponse(success = false, message = "Password must be at least 6 characters")
        }

        // Validate kit assignment
        if (request.assignedKits < 0) {
            return SalesExecutiveResponse(success = false, message = "Assigned kits cannot be negative")
        }

        // Check dealer has enough kits to assign
        val dealerUsedByCustomers = customerRepository.countByDealerId(dealerId).toInt()
        val totalAssignedToSEs = salesExecutiveRepository.findByDealerId(dealerId).sumOf { it.assignedKits }
        val dealerAvailable = dealer.customerLimit - dealerUsedByCustomers - totalAssignedToSEs
        if (request.assignedKits > dealerAvailable) {
            return SalesExecutiveResponse(
                success = false,
                message = "Not enough kits available. Dealer available: $dealerAvailable, requested: ${request.assignedKits}"
            )
        }

        val seId = generateSalesExecutiveId()

        val se = SalesExecutive().apply {
            this.salesExecutiveId = seId
            this.dealerId = dealerId
            this.name = request.name
            this.phone = request.phone
            // Ensure non-null String assignment even if request.password is defined as nullable elsewhere
            this.passwordHash = passwordEncoder.encode(request.password) ?: ""
            this.plainPassword = request.password
            this.status = SalesExecutiveStatus.active
            this.assignedKits = request.assignedKits
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }

        val saved = salesExecutiveRepository.save(se)
        return SalesExecutiveResponse(
            success = true,
            message = "Sales executive created successfully",
            salesExecutive = toData(saved)
        )
    }

    fun getSalesExecutive(dealerId: String, seId: String): SalesExecutiveResponse {
        val se = salesExecutiveRepository.findBySalesExecutiveId(seId).orElse(null)
            ?: return SalesExecutiveResponse(success = false, message = "Sales executive not found")
        if (se.dealerId != dealerId) {
            return SalesExecutiveResponse(success = false, message = "Access denied")
        }
        return SalesExecutiveResponse(success = true, message = "OK", salesExecutive = toData(se))
    }

    fun listSalesExecutives(dealerId: String): SalesExecutiveListResponse {
        val dealer = dealerRepository.findByDealerId(dealerId).orElse(null)
            ?: return SalesExecutiveListResponse(success = false, message = "Dealer not found")

        val seList = salesExecutiveRepository.findByDealerId(dealerId)
        val totalAssigned = seList.sumOf { it.assignedKits }
        val dealerUsedByCustomers = customerRepository.countByDealerId(dealerId).toInt()
        val dealerRemaining = (dealer.customerLimit - dealerUsedByCustomers - totalAssigned).coerceAtLeast(0)

        return SalesExecutiveListResponse(
            success = true,
            message = "OK",
            salesExecutives = seList.map { toData(it) },
            totalAssignedKits = totalAssigned,
            dealerTotalKits = dealer.customerLimit,
            dealerRemainingKits = dealerRemaining
        )
    }

    @Transactional
    fun updateSalesExecutive(dealerId: String, seId: String, request: UpdateSalesExecutiveRequest): SalesExecutiveResponse {
        val se = salesExecutiveRepository.findBySalesExecutiveId(seId).orElse(null)
            ?: return SalesExecutiveResponse(success = false, message = "Sales executive not found")
        if (se.dealerId != dealerId) {
            return SalesExecutiveResponse(success = false, message = "Access denied")
        }

        request.name?.let { se.name = it }

        if (request.phone != null && request.phone != se.phone) {
            if (!request.phone.matches(Regex("^[0-9]{10}$"))) {
                return SalesExecutiveResponse(success = false, message = "Phone number must be exactly 10 digits")
            }
            if (salesExecutiveRepository.existsByDealerIdAndPhone(dealerId, request.phone)) {
                return SalesExecutiveResponse(success = false, message = "Phone number already in use")
            }
            se.phone = request.phone
        }

        if (request.assignedKits != null) {
            if (request.assignedKits < 0) {
                return SalesExecutiveResponse(success = false, message = "Assigned kits cannot be negative")
            }
            // Check SE hasn't used more than the new limit
            val seUsed = customerRepository.countBySalesExecutiveId(seId).toInt()
            if (request.assignedKits < seUsed) {
                return SalesExecutiveResponse(
                    success = false,
                    message = "Cannot reduce kits below used count ($seUsed customers exist)"
                )
            }
            // Check dealer capacity
            val dealer = dealerRepository.findByDealerId(dealerId).orElse(null)
                ?: return SalesExecutiveResponse(success = false, message = "Dealer not found")
            val dealerUsed = customerRepository.countByDealerId(dealerId).toInt()
            val otherSEAssigned = salesExecutiveRepository.findByDealerId(dealerId)
                .filter { it.salesExecutiveId != seId }
                .sumOf { it.assignedKits }
            val dealerAvail = dealer.customerLimit - dealerUsed - otherSEAssigned
            if (request.assignedKits > dealerAvail + se.assignedKits) {
                return SalesExecutiveResponse(
                    success = false,
                    message = "Not enough dealer kits. Available (including current SE allocation): ${dealerAvail + se.assignedKits}"
                )
            }
            se.assignedKits = request.assignedKits
        }

        request.status?.let {
            try {
                val newStatus = SalesExecutiveStatus.valueOf(it.lowercase())
                se.status = newStatus
                if (newStatus == SalesExecutiveStatus.suspended || newStatus == SalesExecutiveStatus.inactive) {
                    se.currentTokenId = null
                    se.isLoggedIn = false
                }
            } catch (e: IllegalArgumentException) {
                return SalesExecutiveResponse(success = false, message = "Invalid status: $it")
            }
        }

        se.updatedAt = Instant.now()
        val saved = salesExecutiveRepository.save(se)
        return SalesExecutiveResponse(success = true, message = "Sales executive updated", salesExecutive = toData(saved))
    }

    @Transactional
    fun deleteSalesExecutive(dealerId: String, seId: String): SalesExecutiveResponse {
        val se = salesExecutiveRepository.findBySalesExecutiveId(seId).orElse(null)
            ?: return SalesExecutiveResponse(success = false, message = "Sales executive not found")
        if (se.dealerId != dealerId) {
            return SalesExecutiveResponse(success = false, message = "Access denied")
        }
        // Check if SE has customers
        val seCustomerCount = customerRepository.countBySalesExecutiveId(seId).toInt()
        if (seCustomerCount > 0) {
            return SalesExecutiveResponse(
                success = false,
                message = "Cannot delete: $seCustomerCount customers are assigned to this sales executive. Reassign or delete customers first."
            )
        }
        salesExecutiveRepository.delete(se)
        return SalesExecutiveResponse(success = true, message = "Sales executive deleted")
    }

    /** Force logout a sales executive (used by dealer) */
    @Transactional
    fun forceLogout(dealerId: String, seId: String): SalesExecutiveResponse {
        val se = salesExecutiveRepository.findBySalesExecutiveId(seId).orElse(null)
            ?: return SalesExecutiveResponse(success = false, message = "Sales executive not found")
        if (se.dealerId != dealerId) {
            return SalesExecutiveResponse(success = false, message = "Access denied")
        }
        se.currentTokenId = null
        se.isLoggedIn = false
        se.updatedAt = Instant.now()
        salesExecutiveRepository.save(se)
        return SalesExecutiveResponse(success = true, message = "Sales executive logged out", salesExecutive = toData(se))
    }

    // ---------- Helpers ----------

    private fun toData(se: SalesExecutive): SalesExecutiveData {
        val usedKits = customerRepository.countBySalesExecutiveId(se.salesExecutiveId).toInt()
        return SalesExecutiveData(
            salesExecutiveId = se.salesExecutiveId,
            dealerId = se.dealerId,
            name = se.name,
            phone = se.phone,
            status = se.status.name,
            assignedKits = se.assignedKits,
            usedKits = usedKits,
            availableKits = (se.assignedKits - usedKits).coerceAtLeast(0),
            isPinSet = se.isPinSet,
            isLoggedIn = se.isLoggedIn,
            lastLogin = se.lastLogin?.toString(),
            createdAt = se.createdAt?.toString() ?: "",
            updatedAt = se.updatedAt?.toString() ?: "",
            password = se.plainPassword
        )
    }

    private fun generateSalesExecutiveId(): String {
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        val random = UUID.randomUUID().toString().replace("-", "").uppercase().take(4)
        return "SE$timestamp$random"
    }
}

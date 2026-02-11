package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.repository.*
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AdminMaintenanceService(
    private val customerRepository: CustomerRepository,
    private val dealerRepository: DealerRepository,
    private val salesExecutiveRepository: SalesExecutiveRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val deviceCommandRepository: DeviceCommandRepository,
    private val toggleStateRepository: ToggleStateRepository,
    private val simDetailsRepository: SimDetailsRepository,
    private val paymentHistoryRepository: PaymentHistoryRepository,
    private val loanDetailsRepository: LoanDetailsRepository,
    private val panDetailsRepository: PANDetailsRepository,
    private val aadharDetailsRepository: AadharDetailsRepository,
    private val activityRepository: ActivityRepository,
    private val supportTicketRepository: SupportTicketRepository,
    private val ticketMessageRepository: TicketMessageRepository,
    private val contactSubmissionRepository: ContactSubmissionRepository,
    private val dealerPaymentRepository: DealerPaymentRepository,
    private val emiNotificationRepository: EmiNotificationRepository,
    private val deviceOwnerConfigRepository: DeviceOwnerConfigRepository
) {

    data class WipeResponse(
        val success: Boolean,
        val message: String
    )

    /**
     * Secret value required to authorize a full data wipe.
     * When empty, no secret check is enforced (NOT recommended for production).
     *
     * Configure via application properties or environment variable:
     * DB_WIPE_SECRET=your-strong-secret
     */
    @Value("\${DB_WIPE_SECRET:}")
    private lateinit var wipeSecret: String

    /**
     * Danger: Delete (almost) all business data from the database.
     *
     * This is intended for controlled admin use only (e.g. staging reset).
     * It wipes customers, dealers, sales executives, commands, device status,
     * SIM history, loan/docs, payments, activities, tickets, contact submissions,
     * EMI notifications and device owner config.
     *
     * Admin users MUST pass the correct secret; if DB_WIPE_SECRET is blank,
     * the check is skipped.
     */
    @Transactional
    fun wipeAllData(providedSecret: String?): WipeResponse {
        if (this::wipeSecret.isInitialized && wipeSecret.isNotBlank()) {
            if (providedSecret.isNullOrBlank() || providedSecret != wipeSecret) {
                return WipeResponse(
                    success = false,
                    message = "Invalid or missing wipe secret"
                )
            }
        }

        // Child tables first to satisfy FK constraints
        deviceCommandRepository.deleteAll()
        toggleStateRepository.deleteAll()
        deviceStatusRepository.deleteAll()
        simDetailsRepository.deleteAll()
        paymentHistoryRepository.deleteAll()
        loanDetailsRepository.deleteAll()
        panDetailsRepository.deleteAll()
        aadharDetailsRepository.deleteAll()
        activityRepository.deleteAll()
        ticketMessageRepository.deleteAll()
        supportTicketRepository.deleteAll()
        contactSubmissionRepository.deleteAll()
        dealerPaymentRepository.deleteAll()
        emiNotificationRepository.deleteAll()

        // Core business entities
        customerRepository.deleteAll()
        salesExecutiveRepository.deleteAll()
        dealerRepository.deleteAll()

        // Device Owner config (force fresh upload after wipe)
        deviceOwnerConfigRepository.deleteAll()

        return WipeResponse(
            success = true,
            message = "All application data wiped successfully"
        )
    }
}


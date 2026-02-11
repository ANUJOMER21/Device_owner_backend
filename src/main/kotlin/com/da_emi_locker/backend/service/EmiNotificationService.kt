package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.*
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.EmiNotificationRepository
import com.da_emi_locker.backend.repository.LoanDetailsRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Service
class EmiNotificationService(
    private val loanDetailsRepository: LoanDetailsRepository,
    private val customerRepository: CustomerRepository,
    private val emiNotificationRepository: EmiNotificationRepository,
    private val fcmService: FCMService
) {

    private val logger = LoggerFactory.getLogger(EmiNotificationService::class.java)

    // ── Response DTOs ──────────────────────────────────────────────────

    data class EmiDueCustomerDto(
        val customerId: String,
        val customerName: String,
        val phone: String,
        val email: String?,
        val loanId: String,
        val emiAmount: BigDecimal,
        val dueDate: LocalDate,
        val daysUntilDue: Long,
        val loanStatus: String,
        val totalPaid: BigDecimal,
        val remainingAmount: BigDecimal,
        val hasFcmToken: Boolean
    )

    data class EmiDueResponse(
        val success: Boolean,
        val message: String,
        val totalCustomers: Int,
        val overdueCustomers: Int,
        val dueTodayCustomers: Int,
        val upcomingCustomers: Int,
        val customers: List<EmiDueCustomerDto>
    )

    data class SendReminderResponse(
        val success: Boolean,
        val message: String,
        val notificationId: Long? = null
    )

    data class NotificationHistoryDto(
        val id: Long,
        val customerId: String,
        val customerName: String?,
        val loanId: String,
        val dueDate: LocalDate,
        val notificationType: String,
        val sentAt: Instant?,
        val success: Boolean,
        val errorMessage: String?,
        val sentBy: String?
    )

    data class NotificationHistoryResponse(
        val notifications: List<NotificationHistoryDto>,
        val totalCount: Long,
        val page: Int,
        val pageSize: Int
    )

    // ── Scheduled: send auto-reminders 1 day before due ─────────────

    /**
     * Called by the scheduler every day at a configured time.
     * Finds all active loans whose next EMI due date is tomorrow and
     * sends an FCM push notification to the customer.
     */
    @Transactional
    fun sendAutoEmiReminders() {
        val tomorrow = LocalDate.now().plusDays(1)
        logger.info("Running automatic EMI reminder job for due date: $tomorrow")

        val activeLoans = loanDetailsRepository.findByStatus(LoanStatus.active)
        var sent = 0
        var skipped = 0
        var failed = 0

        for (loan in activeLoans) {
            val nextDueDate = computeNextDueDate(loan) ?: continue

            if (nextDueDate != tomorrow) continue

            // Skip if we already sent a successful auto-reminder for this due date
            val alreadySent = emiNotificationRepository
                .existsByCustomerIdAndLoanIdAndDueDateAndNotificationTypeAndSuccessTrue(
                    loan.customerId, loan.loanId, nextDueDate, EmiNotificationType.AUTO_REMINDER
                )
            if (alreadySent) {
                skipped++
                continue
            }

            val customer = customerRepository.findByCustomerId(loan.customerId).orElse(null)
            if (customer == null) {
                logger.warn("Customer ${loan.customerId} not found for loan ${loan.loanId}")
                continue
            }

            val result = sendEmiNotification(
                customer = customer,
                loan = loan,
                dueDate = nextDueDate,
                notificationType = EmiNotificationType.AUTO_REMINDER,
                sentBy = "SYSTEM"
            )

            if (result.success) sent++ else failed++
        }

        logger.info("EMI reminder job completed: sent=$sent, skipped=$skipped, failed=$failed")
    }

    // ── Dealer API: customers with EMI due recently ─────────────────

    /**
     * Returns the dealer's customers who have EMI due within the
     * requested window (default: 7 days ahead and 7 days overdue).
     */
    fun getCustomersWithEmiDue(
        dealerId: String,
        daysAhead: Int = 7,
        daysBehind: Int = 7
    ): EmiDueResponse {
        val today = LocalDate.now()
        val windowStart = today.minusDays(daysBehind.toLong())
        val windowEnd = today.plusDays(daysAhead.toLong())

        val activeLoans = loanDetailsRepository.findByDealerIdAndStatus(dealerId, LoanStatus.active)

        // Batch-fetch all relevant customers
        val customerIds = activeLoans.map { it.customerId }.distinct()
        val customerMap = customerRepository.findByCustomerIdIn(customerIds)
            .associateBy { it.customerId }

        val dueCustomers = mutableListOf<EmiDueCustomerDto>()

        for (loan in activeLoans) {
            val nextDueDate = computeNextDueDate(loan) ?: continue

            if (nextDueDate.isBefore(windowStart) || nextDueDate.isAfter(windowEnd)) continue

            val customer = customerMap[loan.customerId] ?: continue

            val daysUntilDue = ChronoUnit.DAYS.between(today, nextDueDate)

            dueCustomers.add(
                EmiDueCustomerDto(
                    customerId = customer.customerId,
                    customerName = customer.name,
                    phone = customer.phone,
                    email = customer.email,
                    loanId = loan.loanId,
                    emiAmount = loan.monthlyEmi ?: loan.emiAmount,
                    dueDate = nextDueDate,
                    daysUntilDue = daysUntilDue,
                    loanStatus = loan.status.name,
                    totalPaid = loan.totalPaid,
                    remainingAmount = loan.remainingAmount,
                    hasFcmToken = !customer.fcmToken.isNullOrBlank()
                )
            )
        }

        // Sort: overdue first (most overdue at top), then upcoming (soonest first)
        dueCustomers.sortBy { it.daysUntilDue }

        // We no longer rely on "overdue" counts for business logic; keep 0 to avoid
        // implying payment status. The UI can still use dueDate for context.
        val overdue = 0
        val dueToday = dueCustomers.count { it.daysUntilDue == 0L }
        val upcoming = dueCustomers.count { it.daysUntilDue > 0 }

        return EmiDueResponse(
            success = true,
            message = "Found ${dueCustomers.size} customer(s) with EMI due",
            totalCustomers = dueCustomers.size,
            overdueCustomers = overdue,
            dueTodayCustomers = dueToday,
            upcomingCustomers = upcoming,
            customers = dueCustomers
        )
    }

    // ── Dealer API: manually send EMI reminder to a customer ────────

    /**
     * Dealer triggers a push notification to a specific customer for
     * their active loan. Returns success/failure.
     */
    @Transactional
    fun sendDealerReminder(dealerId: String, customerId: String, loanId: String?): SendReminderResponse {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return SendReminderResponse(false, "Customer not found")

        // Verify customer belongs to this dealer
        if (customer.dealerId != dealerId) {
            return SendReminderResponse(false, "Customer does not belong to this dealer")
        }

        // Find the loan
        val loan: LoanDetails = if (loanId != null) {
            loanDetailsRepository.findByLoanId(loanId).orElse(null)
                ?: return SendReminderResponse(false, "Loan not found")
        } else {
            // Pick the first active loan for this customer
            loanDetailsRepository.findByCustomerIdAndStatus(customerId, LoanStatus.active)
                .firstOrNull()
                ?: return SendReminderResponse(false, "No active loan found for this customer")
        }

        if (loan.customerId != customerId) {
            return SendReminderResponse(false, "Loan does not belong to this customer")
        }

        val nextDueDate = computeNextDueDate(loan)
            ?: return SendReminderResponse(false, "Could not determine next EMI due date")

        val result = sendEmiNotification(
            customer = customer,
            loan = loan,
            dueDate = nextDueDate,
            notificationType = EmiNotificationType.DEALER_REMINDER,
            sentBy = dealerId
        )

        return result
    }

    // ── Dealer API: notification history ───────────────────────────

    /**
     * Returns paginated notification history for a dealer's customers.
     * Optionally filtered by customerId.
     */
    fun getNotificationHistory(
        dealerId: String,
        customerId: String?,
        page: Int,
        pageSize: Int
    ): NotificationHistoryResponse {
        val pageable = PageRequest.of(page, pageSize)

        val notificationsPage = if (customerId != null) {
            emiNotificationRepository.findByDealerIdAndCustomerIdOrderBySentAtDesc(dealerId, customerId, pageable)
        } else {
            emiNotificationRepository.findByDealerIdOrderBySentAtDesc(dealerId, pageable)
        }

        // Batch-fetch customer names
        val customerIds = notificationsPage.content.map { it.customerId }.distinct()
        val customerMap = customerRepository.findByCustomerIdIn(customerIds).associateBy { it.customerId }

        val dtos = notificationsPage.content.map { n ->
            NotificationHistoryDto(
                id = n.id ?: 0,
                customerId = n.customerId,
                customerName = customerMap[n.customerId]?.name,
                loanId = n.loanId,
                dueDate = n.dueDate!!,
                notificationType = n.notificationType.name,
                sentAt = n.sentAt,
                success = n.success,
                errorMessage = n.errorMessage,
                sentBy = n.sentBy
            )
        }

        return NotificationHistoryResponse(
            notifications = dtos,
            totalCount = notificationsPage.totalElements,
            page = page,
            pageSize = pageSize
        )
    }

    // ── Admin API: all customers with EMI due ───────────────────────

    /**
     * Returns all customers with EMI due across all dealers (or filtered by dealerId).
     * Used by admin panel.
     */
    fun getAllCustomersWithEmiDue(
        dealerId: String?,
        daysAhead: Int = 7,
        daysBehind: Int = 7
    ): EmiDueResponse {
        val today = LocalDate.now()
        val windowStart = today.minusDays(daysBehind.toLong())
        val windowEnd = today.plusDays(daysAhead.toLong())

        val activeLoans = if (dealerId != null) {
            loanDetailsRepository.findByDealerIdAndStatus(dealerId, LoanStatus.active)
        } else {
            loanDetailsRepository.findByStatus(LoanStatus.active)
        }

        val customerIds = activeLoans.map { it.customerId }.distinct()
        val customerMap = customerRepository.findByCustomerIdIn(customerIds).associateBy { it.customerId }

        val dueCustomers = mutableListOf<EmiDueCustomerDto>()

        for (loan in activeLoans) {
            val nextDueDate = computeNextDueDate(loan) ?: continue
            if (nextDueDate.isBefore(windowStart) || nextDueDate.isAfter(windowEnd)) continue
            val customer = customerMap[loan.customerId] ?: continue
            val daysUntilDue = ChronoUnit.DAYS.between(today, nextDueDate)

            dueCustomers.add(
                EmiDueCustomerDto(
                    customerId = customer.customerId,
                    customerName = customer.name,
                    phone = customer.phone,
                    email = customer.email,
                    loanId = loan.loanId,
                    emiAmount = loan.monthlyEmi ?: loan.emiAmount,
                    dueDate = nextDueDate,
                    daysUntilDue = daysUntilDue,
                    loanStatus = loan.status.name,
                    totalPaid = loan.totalPaid,
                    remainingAmount = loan.remainingAmount,
                    hasFcmToken = !customer.fcmToken.isNullOrBlank()
                )
            )
        }

        dueCustomers.sortBy { it.daysUntilDue }

        // We no longer rely on "overdue" counts for business logic; keep 0 to avoid
        // implying payment status. The UI can still use dueDate for context.
        val overdue = 0
        val dueToday = dueCustomers.count { it.daysUntilDue == 0L }
        val upcoming = dueCustomers.count { it.daysUntilDue > 0 }

        return EmiDueResponse(
            success = true,
            message = "Found ${dueCustomers.size} customer(s) with EMI due",
            totalCustomers = dueCustomers.size,
            overdueCustomers = overdue,
            dueTodayCustomers = dueToday,
            upcomingCustomers = upcoming,
            customers = dueCustomers
        )
    }

    // ── Admin API: send reminder (no dealer ownership check) ────────

    @Transactional
    fun sendAdminReminder(customerId: String, loanId: String?): SendReminderResponse {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return SendReminderResponse(false, "Customer not found")

        val loan: LoanDetails = if (loanId != null) {
            loanDetailsRepository.findByLoanId(loanId).orElse(null)
                ?: return SendReminderResponse(false, "Loan not found")
        } else {
            loanDetailsRepository.findByCustomerIdAndStatus(customerId, LoanStatus.active)
                .firstOrNull()
                ?: return SendReminderResponse(false, "No active loan found for this customer")
        }

        if (loan.customerId != customerId) {
            return SendReminderResponse(false, "Loan does not belong to this customer")
        }

        val nextDueDate = computeNextDueDate(loan)
            ?: return SendReminderResponse(false, "Could not determine next EMI due date")

        return sendEmiNotification(
            customer = customer,
            loan = loan,
            dueDate = nextDueDate,
            notificationType = EmiNotificationType.DEALER_REMINDER,
            sentBy = "ADMIN"
        )
    }

    // ── Admin API: notification history ─────────────────────────────

    fun getAdminNotificationHistory(
        customerId: String?,
        dealerId: String?,
        page: Int,
        pageSize: Int
    ): NotificationHistoryResponse {
        val pageable = PageRequest.of(page, pageSize)

        val notificationsPage = when {
            customerId != null ->
                emiNotificationRepository.findByCustomerIdOrderBySentAtDesc(customerId, pageable)
            dealerId != null ->
                emiNotificationRepository.findByDealerCustomersOrderBySentAtDesc(dealerId, pageable)
            else ->
                emiNotificationRepository.findAllByOrderBySentAtDesc(pageable)
        }

        val customerIds = notificationsPage.content.map { it.customerId }.distinct()
        val customerMap = customerRepository.findByCustomerIdIn(customerIds).associateBy { it.customerId }

        val dtos = notificationsPage.content.map { n ->
            NotificationHistoryDto(
                id = n.id ?: 0,
                customerId = n.customerId,
                customerName = customerMap[n.customerId]?.name,
                loanId = n.loanId,
                dueDate = n.dueDate!!,
                notificationType = n.notificationType.name,
                sentAt = n.sentAt,
                success = n.success,
                errorMessage = n.errorMessage,
                sentBy = n.sentBy
            )
        }

        return NotificationHistoryResponse(
            notifications = dtos,
            totalCount = notificationsPage.totalElements,
            page = page,
            pageSize = pageSize
        )
    }

    // ── Internal helpers ────────────────────────────────────────────

    /**
     * Computes the next EMI due date for a loan.
     *
     * Logic:
     * 1. If [LoanDetails.emiDate] is set and is today or in the future, use it.
     * 2. Otherwise derive from the loan start date: EMI falls on the
     *    same day-of-month as startDate every month, starting from the
     *    month after startDate. Walk forward until we find the next date
     *    that is today or later (or still within a small overdue window).
     */
    private fun computeNextDueDate(loan: LoanDetails): LocalDate? {
        val today = LocalDate.now()

        // If emiDate is explicitly set and is still relevant
        val emiDate = loan.emiDate
        if (emiDate != null) {
            // emiDate stores the day-of-month reference
            val dayOfMonth = emiDate.dayOfMonth
            // Walk from today's month backwards 1 month to catch overdue,
            // then forwards to find the next matching due date
            val startSearch = YearMonth.from(today).minusMonths(1)
            for (offset in 0L..loan.tenureMonths + 2L) {
                val ym = startSearch.plusMonths(offset)
                val day = minOf(dayOfMonth, ym.lengthOfMonth())
                val candidate = ym.atDay(day)
                // Return first candidate that is today or later
                if (!candidate.isBefore(today.minusDays(7))) {
                    // Only return if the loan is still active for that period
                    val endDate = loan.endDate
                    if (endDate != null && candidate.isAfter(endDate)) return null
                    return candidate
                }
            }
            return null
        }

        // Fallback: derive from startDate
        val startDate = loan.startDate ?: return null
        val dayOfMonth = startDate.dayOfMonth
        val startSearch = YearMonth.from(today).minusMonths(1)
        for (offset in 0L..loan.tenureMonths + 2L) {
            val ym = startSearch.plusMonths(offset)
            val day = minOf(dayOfMonth, ym.lengthOfMonth())
            val candidate = ym.atDay(day)
            if (!candidate.isBefore(today.minusDays(7)) && !candidate.isBefore(startDate)) {
                val endDate = loan.endDate
                if (endDate != null && candidate.isAfter(endDate)) return null
                return candidate
            }
        }
        return null
    }

    /**
     * Sends an FCM notification to the customer and records it in
     * the emi_notifications table.
     */
    private fun sendEmiNotification(
        customer: Customer,
        loan: LoanDetails,
        dueDate: LocalDate,
        notificationType: EmiNotificationType,
        sentBy: String
    ): SendReminderResponse {
        val notification = EmiNotification().apply {
            this.customerId = customer.customerId
            this.loanId = loan.loanId
            this.dueDate = dueDate
            this.notificationType = notificationType
            this.sentBy = sentBy
            this.sentAt = Instant.now()
        }

        val token = customer.fcmToken
        if (token.isNullOrBlank()) {
            notification.success = false
            notification.errorMessage = "Customer has no FCM token"
            emiNotificationRepository.save(notification)
            logger.warn("No FCM token for customer ${customer.customerId} (loan ${loan.loanId})")
            return SendReminderResponse(
                success = false,
                message = "Customer does not have push notifications enabled (no FCM token)",
                notificationId = notification.id
            )
        }

        val emiAmount = loan.monthlyEmi ?: loan.emiAmount
        val title = "EMI Payment Reminder"
        val body = when {
            dueDate.isEqual(LocalDate.now()) ->
                "Your EMI of ₹$emiAmount is due today. Please make the payment to avoid late charges."
            dueDate.isBefore(LocalDate.now()) ->
                "Your EMI of ₹$emiAmount was due on $dueDate. Please pay immediately to avoid penalties."
            else ->
                "Your EMI of ₹$emiAmount is due on $dueDate. Please ensure timely payment."
        }

        val data = mapOf(
            "type" to "EMI_REMINDER",
            "loanId" to loan.loanId,
            "customerId" to customer.customerId,
            "emiAmount" to emiAmount.toPlainString(),
            "dueDate" to dueDate.toString(),
            "notificationType" to notificationType.name
        )

        val fcmResponse = fcmService.sendNotification(
            FCMService.FCMNotification(
                token = token,
                title = title,
                body = body,
                data = data
            )
        )

        notification.success = fcmResponse.success
        if (!fcmResponse.success) {
            notification.errorMessage = fcmResponse.error
        }
        emiNotificationRepository.save(notification)

        return if (fcmResponse.success) {
            logger.info("EMI reminder sent to ${customer.customerId} for loan ${loan.loanId}, due $dueDate")
            SendReminderResponse(
                success = true,
                message = "EMI reminder notification sent successfully",
                notificationId = notification.id
            )
        } else {
            logger.error("Failed to send EMI reminder to ${customer.customerId}: ${fcmResponse.error}")
            SendReminderResponse(
                success = false,
                message = "Failed to send notification: ${fcmResponse.error}",
                notificationId = notification.id
            )
        }
    }
}

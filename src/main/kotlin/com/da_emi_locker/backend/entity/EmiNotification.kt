package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

/**
 * Tracks EMI reminder notifications sent to customers.
 * Used both for automatic (scheduled 1 day before due date) and
 * manual (dealer-initiated) reminders.
 */
@Entity
@Table(name = "emi_notifications")
class EmiNotification : BaseEntity() {

    @Column(name = "customer_id", nullable = false, length = 50)
    var customerId: String = ""

    @Column(name = "loan_id", nullable = false, length = 50)
    var loanId: String = ""

    @Column(name = "due_date", nullable = false)
    var dueDate: LocalDate? = null

    @Column(name = "notification_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    var notificationType: EmiNotificationType = EmiNotificationType.AUTO_REMINDER

    @Column(name = "sent_at")
    var sentAt: Instant? = null

    @Column(nullable = false)
    var success: Boolean = false

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null

    /** dealer_id for manual reminders, "SYSTEM" for automatic reminders */
    @Column(name = "sent_by", length = 50)
    var sentBy: String? = null
}

enum class EmiNotificationType {
    AUTO_REMINDER,
    DEALER_REMINDER
}

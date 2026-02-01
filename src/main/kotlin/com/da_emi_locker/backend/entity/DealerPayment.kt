package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Dealer payment entity
 */
@Entity
@Table(name = "dealer_payments")
class DealerPayment : BaseEntity() {
    
    @Column(name = "dealer_id", nullable = false, length = 50)
    var dealerId: String = ""
    
    @Column(name = "payment_id", unique = true, nullable = false, length = 50)
    var paymentId: String = ""
    
    @Column(nullable = false, precision = 15, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO
    
    @Column(name = "payment_method", nullable = false, length = 50)
    var paymentMethod: String = ""
    
    @Column(name = "payment_status", length = 20)
    @Enumerated(EnumType.STRING)
    var paymentStatus: PaymentStatus = PaymentStatus.pending
    
    @Column(name = "transaction_id", length = 100)
    var transactionId: String? = null
    
    @Column(name = "payment_date")
    var paymentDate: Instant? = null
    
    @Column(name = "due_date")
    var dueDate: LocalDate? = null
    
    @Column(columnDefinition = "TEXT")
    var description: String? = null
    
    @Column(columnDefinition = "TEXT")
    var notes: String? = null

    /** Number of kits for this transaction (assigned or reversed); null treated as 1. */
    @Column(name = "number_of_kits")
    var numberOfKits: Int? = null

    /** Refund amount (money) when this is a reversal/removal; null for normal payments. */
    @Column(name = "refund_amount", precision = 15, scale = 2)
    var refundAmount: BigDecimal? = null

    /** Medium of refund (e.g. cash, UPI, bank_transfer) when reversing kits; null otherwise. */
    @Column(name = "refund_medium", length = 50)
    var refundMedium: String? = null
}

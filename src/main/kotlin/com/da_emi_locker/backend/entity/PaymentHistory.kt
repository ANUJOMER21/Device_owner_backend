package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Payment history entity
 */
@Entity
@Table(name = "payment_history")
class PaymentHistory : BaseEntity() {
    
    @Column(name = "customer_id", nullable = false, length = 50)
    var customerId: String = ""
    
    @Column(name = "loan_id", length = 50)
    var loanId: String? = null
    
    @Column(name = "payment_id", unique = true, nullable = false, length = 50)
    var paymentId: String = ""
    
    @Column(nullable = false, precision = 10, scale = 2)
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
    
    @Column(name = "emi_number")
    var emiNumber: Int? = null
    
    @Column(columnDefinition = "TEXT")
    var notes: String? = null
}

enum class PaymentStatus {
    pending,
    completed,
    failed,
    refunded
}

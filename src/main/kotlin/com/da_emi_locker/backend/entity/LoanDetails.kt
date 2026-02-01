package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Customer loan/EMI details entity
 */
@Entity
@Table(name = "customer_loan_details")
class LoanDetails : BaseEntity() {
    
    @Column(name = "customer_id", nullable = false, length = 50)
    var customerId: String = ""
    
    @Column(name = "loan_id", unique = true, nullable = false, length = 50)
    var loanId: String = ""
    
    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    var principalAmount: BigDecimal = BigDecimal.ZERO
    
    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    var interestRate: BigDecimal = BigDecimal.ZERO
    
    @Column(name = "tenure_months", nullable = false)
    var tenureMonths: Int = 0
    
    @Column(name = "emi_amount", nullable = false, precision = 10, scale = 2)
    var emiAmount: BigDecimal = BigDecimal.ZERO
    
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate? = null
    
    @Column(name = "end_date")
    var endDate: LocalDate? = null
    
    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    var status: LoanStatus = LoanStatus.active
    
    @Column(name = "total_paid", precision = 15, scale = 2)
    var totalPaid: BigDecimal = BigDecimal.ZERO
    
    @Column(name = "remaining_amount", nullable = false, precision = 15, scale = 2)
    var remainingAmount: BigDecimal = BigDecimal.ZERO
    
    // Additional fields for loan details
    @Column(name = "product_price", precision = 15, scale = 2)
    var productPrice: BigDecimal? = null
    
    @Column(name = "down_payment", precision = 15, scale = 2)
    var downPayment: BigDecimal? = null
    
    @Column(name = "loan_amount", precision = 15, scale = 2)
    var loanAmount: BigDecimal? = null
    
    @Column(name = "rate_of_interest", precision = 5, scale = 2)
    var rateOfInterest: BigDecimal? = null
    
    @Column(name = "monthly_emi", precision = 10, scale = 2)
    var monthlyEmi: BigDecimal? = null
    
    @Column(name = "emi_date")
    var emiDate: LocalDate? = null
    
    @Column(columnDefinition = "TEXT")
    var remark: String? = null
}

enum class LoanStatus {
    active,
    completed,
    defaulted,
    closed
}

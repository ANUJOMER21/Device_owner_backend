package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.LoanDetails
import com.da_emi_locker.backend.entity.LoanStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface LoanDetailsRepository : JpaRepository<LoanDetails, Long> {
    
    fun findByCustomerId(customerId: String): List<LoanDetails>
    
    fun findByLoanId(loanId: String): Optional<LoanDetails>
    
    fun existsByLoanId(loanId: String): Boolean
    
    fun findByCustomerIdAndStatus(customerId: String, status: LoanStatus): List<LoanDetails>
}

package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.LoanDetails
import com.da_emi_locker.backend.entity.LoanStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface LoanDetailsRepository : JpaRepository<LoanDetails, Long> {
    
    fun findByCustomerId(customerId: String): List<LoanDetails>
    
    fun findByLoanId(loanId: String): Optional<LoanDetails>
    
    fun existsByLoanId(loanId: String): Boolean
    
    fun findByCustomerIdAndStatus(customerId: String, status: LoanStatus): List<LoanDetails>

    /** All active loans (for scheduled EMI reminder job). */
    fun findByStatus(status: LoanStatus): List<LoanDetails>

    /**
     * Active loans for customers belonging to a specific dealer.
     * Used to show the dealer which of their customers have upcoming EMIs.
     */
    @Query("""
        SELECT ld FROM LoanDetails ld
        WHERE ld.status = :status
          AND ld.customerId IN (
              SELECT c.customerId FROM Customer c WHERE c.dealerId = :dealerId
          )
    """)
    fun findByDealerIdAndStatus(
        @Param("dealerId") dealerId: String,
        @Param("status") status: LoanStatus
    ): List<LoanDetails>
}

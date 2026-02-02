package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.PaymentHistory
import com.da_emi_locker.backend.entity.PaymentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PaymentHistoryRepository : JpaRepository<PaymentHistory, Long> {

    fun findByCustomerId(customerId: String): List<PaymentHistory>
    
    @Query("""
        SELECT ph FROM PaymentHistory ph
        WHERE ph.customerId IN (
            SELECT c.customerId FROM Customer c WHERE c.dealerId = :dealerId
        )
        ORDER BY ph.paymentDate DESC NULLS LAST, ph.createdAt DESC
    """)
    fun findByDealerId(@Param("dealerId") dealerId: String, pageable: Pageable): Page<PaymentHistory>
    
    @Query("""
        SELECT ph FROM PaymentHistory ph
        WHERE ph.customerId IN (
            SELECT c.customerId FROM Customer c WHERE c.dealerId = :dealerId
        ) AND ph.paymentStatus = :status
        ORDER BY ph.paymentDate DESC NULLS LAST, ph.createdAt DESC
    """)
    fun findByDealerIdAndStatus(
        @Param("dealerId") dealerId: String,
        @Param("status") status: PaymentStatus,
        pageable: Pageable
    ): Page<PaymentHistory>
    
    @Query("""
        SELECT ph FROM PaymentHistory ph
        WHERE ph.customerId IN (
            SELECT c.customerId FROM Customer c WHERE c.dealerId = :dealerId
        ) AND ph.paymentDate >= :startDate AND ph.paymentDate <= :endDate
        ORDER BY ph.paymentDate DESC NULLS LAST, ph.createdAt DESC
    """)
    fun findByDealerIdAndDateRange(
        @Param("dealerId") dealerId: String,
        @Param("startDate") startDate: Instant,
        @Param("endDate") endDate: Instant,
        pageable: Pageable
    ): Page<PaymentHistory>
}

package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.DealerPayment
import com.da_emi_locker.backend.entity.PaymentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant

@Repository
interface DealerPaymentRepository : JpaRepository<DealerPayment, Long> {
    
    fun findByDealerId(dealerId: String, pageable: Pageable): Page<DealerPayment>

    fun findByPaymentDateBetween(start: Instant, end: Instant, pageable: Pageable): Page<DealerPayment>

    fun findByDealerIdAndPaymentDateBetween(dealerId: String, start: Instant, end: Instant, pageable: Pageable): Page<DealerPayment>
    
    fun findByDealerIdAndPaymentStatus(
        dealerId: String,
        status: PaymentStatus,
        pageable: Pageable
    ): Page<DealerPayment>
    
    fun findByPaymentId(paymentId: String): DealerPayment?
    
    @Query("SELECT COALESCE(SUM(dp.amount), 0) FROM DealerPayment dp WHERE dp.dealerId = :dealerId")
    fun sumAmountByDealerId(@Param("dealerId") dealerId: String): BigDecimal?
    
    @Query(value = "SELECT CAST(COALESCE(SUM(COALESCE(dp.number_of_kits, 1)), 0) AS BIGINT) FROM dealer_payments dp WHERE dp.dealer_id = :dealerId AND dp.amount >= 0", nativeQuery = true)
    fun sumKitCountForPositiveAmount(@Param("dealerId") dealerId: String): Long?
    
    @Query(value = "SELECT CAST(COALESCE(SUM(COALESCE(dp.number_of_kits, 1)), 0) AS BIGINT) FROM dealer_payments dp WHERE dp.dealer_id = :dealerId AND dp.amount < 0", nativeQuery = true)
    fun sumKitCountForNegativeAmount(@Param("dealerId") dealerId: String): Long?
    
    fun countByDealerIdAndAmountGreaterThanEqual(dealerId: String, amount: BigDecimal): Long
    
    fun countByDealerIdAndAmountLessThan(dealerId: String, amount: BigDecimal): Long
}

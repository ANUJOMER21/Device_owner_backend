package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.EmiNotification
import com.da_emi_locker.backend.entity.EmiNotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface EmiNotificationRepository : JpaRepository<EmiNotification, Long> {

    fun findByCustomerIdAndLoanIdAndDueDateAndNotificationType(
        customerId: String,
        loanId: String,
        dueDate: LocalDate,
        notificationType: EmiNotificationType
    ): List<EmiNotification>

    fun findByCustomerIdOrderBySentAtDesc(customerId: String): List<EmiNotification>

    fun findByLoanIdOrderBySentAtDesc(loanId: String): List<EmiNotification>

    /**
     * Check if an auto-reminder was already successfully sent for a given
     * customer + loan + due date combination.
     */
    fun existsByCustomerIdAndLoanIdAndDueDateAndNotificationTypeAndSuccessTrue(
        customerId: String,
        loanId: String,
        dueDate: LocalDate,
        notificationType: EmiNotificationType
    ): Boolean

    /** Paginated history for a specific customer. */
    fun findByCustomerIdOrderBySentAtDesc(customerId: String, pageable: Pageable): Page<EmiNotification>

    /** Paginated history for customers belonging to a dealer. */
    @Query("""
        SELECT en FROM EmiNotification en
        WHERE en.customerId IN (
            SELECT c.customerId FROM Customer c WHERE c.dealerId = :dealerId
        )
        ORDER BY en.sentAt DESC
    """)
    fun findByDealerIdOrderBySentAtDesc(
        @Param("dealerId") dealerId: String,
        pageable: Pageable
    ): Page<EmiNotification>

    /** Paginated history for a customer scoped to a dealer. */
    @Query("""
        SELECT en FROM EmiNotification en
        WHERE en.customerId = :customerId
          AND en.customerId IN (
              SELECT c.customerId FROM Customer c WHERE c.dealerId = :dealerId
          )
        ORDER BY en.sentAt DESC
    """)
    fun findByDealerIdAndCustomerIdOrderBySentAtDesc(
        @Param("dealerId") dealerId: String,
        @Param("customerId") customerId: String,
        pageable: Pageable
    ): Page<EmiNotification>

    /** Admin: all notifications, newest first. */
    fun findAllByOrderBySentAtDesc(pageable: Pageable): Page<EmiNotification>

    /** Admin: filtered by dealer's customers. */
    @Query("""
        SELECT en FROM EmiNotification en
        WHERE en.customerId IN (
            SELECT c.customerId FROM Customer c WHERE c.dealerId = :dealerId
        )
        ORDER BY en.sentAt DESC
    """)
    fun findByDealerCustomersOrderBySentAtDesc(
        @Param("dealerId") dealerId: String,
        pageable: Pageable
    ): Page<EmiNotification>
}

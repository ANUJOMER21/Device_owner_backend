package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.Customer
import com.da_emi_locker.backend.entity.CustomerStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface CustomerRepository : JpaRepository<Customer, Long> {
    
    fun findByDealerId(dealerId: String): List<Customer>

    fun findByDealerIdIn(dealerIds: Collection<String>): List<Customer>

    fun findByCustomerIdIn(customerIds: Collection<String>): List<Customer>
    
    fun findByDealerIdAndStatus(dealerId: String, status: CustomerStatus, pageable: Pageable): Page<Customer>
    
    fun findByDealerId(dealerId: String, pageable: Pageable): Page<Customer>
    
    fun findByCustomerId(customerId: String): Optional<Customer>
    
    fun existsByCustomerId(customerId: String): Boolean
    
    fun countByDealerId(dealerId: String): Long
    
    fun existsByImei1(imei1: String): Boolean
    
    fun findByImei1(imei1: String): Optional<Customer>

    @Query("SELECT c FROM Customer c WHERE c.imei1 = :imei OR c.imei2 = :imei")
    fun findByImei(@Param("imei") imei: String): Optional<Customer>

    fun existsByOfflineUnlockCode(offlineUnlockCode: String): Boolean

    @Query(
        """
        SELECT c FROM Customer c
        WHERE (:status IS NULL OR c.status = :status)
          AND (
            :search IS NULL OR :search = '' OR
            LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
            LOWER(COALESCE(c.email, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
            c.phone LIKE CONCAT('%', :search, '%') OR
            c.customerId LIKE CONCAT('%', :search, '%') OR
            c.imei1 LIKE CONCAT('%', :search, '%') OR
            COALESCE(c.imei2, '') LIKE CONCAT('%', :search, '%')
          )
        """
    )
    fun searchAllForAdmin(
        @Param("status") status: CustomerStatus?,
        @Param("search") search: String?,
        pageable: Pageable
    ): Page<Customer>

    @Query(
        """
        SELECT c.customerId
        FROM Customer c
        WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(c.customerId) LIKE LOWER(CONCAT('%', :q, '%'))
        """
    )
    fun findCustomerIdsBySearch(@Param("q") q: String): List<String>
}

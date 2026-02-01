package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.SimDetails
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface SimDetailsRepository : JpaRepository<SimDetails, Long> {

    fun findTopByCustomerIdOrderByCreatedAtDesc(customerId: String): SimDetails?
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String, pageable: Pageable): List<SimDetails>
}

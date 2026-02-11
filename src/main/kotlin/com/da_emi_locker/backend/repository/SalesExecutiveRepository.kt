package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.SalesExecutive
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface SalesExecutiveRepository : JpaRepository<SalesExecutive, Long> {

    fun findBySalesExecutiveId(salesExecutiveId: String): Optional<SalesExecutive>

    fun findByDealerId(dealerId: String): List<SalesExecutive>

    fun findByPhone(phone: String): Optional<SalesExecutive>

    fun findByDealerIdAndPhone(dealerId: String, phone: String): Optional<SalesExecutive>

    fun existsBySalesExecutiveId(salesExecutiveId: String): Boolean

    fun existsByDealerIdAndPhone(dealerId: String, phone: String): Boolean

    fun countByDealerId(dealerId: String): Long
}

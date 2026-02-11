package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.Dealer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface DealerRepository : JpaRepository<Dealer, Long> {
    
    fun findByEmail(email: String): Optional<Dealer>

    fun findByPhone(phone: String): Optional<Dealer>
    
    fun findByDealerId(dealerId: String): Optional<Dealer>
    
    fun findByGstNumber(gstNumber: String): Optional<Dealer>
    
    fun existsByEmail(email: String): Boolean
    
    fun existsByDealerId(dealerId: String): Boolean
    
    fun existsByGstNumber(gstNumber: String): Boolean

    fun findByDealerIdIn(dealerIds: Collection<String>): List<Dealer>

    @Query(
        """
        SELECT d.dealerId
        FROM Dealer d
        WHERE LOWER(d.name) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(d.dealerId) LIKE LOWER(CONCAT('%', :q, '%'))
        """
    )
    fun findDealerIdsBySearch(@Param("q") q: String): List<String>
}

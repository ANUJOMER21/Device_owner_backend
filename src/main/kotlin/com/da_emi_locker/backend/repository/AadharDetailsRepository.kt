package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.AadharDetails
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface AadharDetailsRepository : JpaRepository<AadharDetails, Long> {
    
    fun findByCustomerId(customerId: String): Optional<AadharDetails>
    
    fun existsByAadharNumber(aadharNumber: String): Boolean
    
    fun existsByCustomerId(customerId: String): Boolean
}

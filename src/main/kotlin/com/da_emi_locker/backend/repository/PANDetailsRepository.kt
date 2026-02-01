package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.PANDetails
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface PANDetailsRepository : JpaRepository<PANDetails, Long> {
    
    fun findByCustomerId(customerId: String): Optional<PANDetails>
    
    fun existsByPanNumber(panNumber: String): Boolean
    
    fun existsByCustomerId(customerId: String): Boolean
}

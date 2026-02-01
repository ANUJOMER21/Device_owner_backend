package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.ToggleState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ToggleStateRepository : JpaRepository<ToggleState, Long> {
    
    fun findByDeviceId(deviceId: String): List<ToggleState>
    
    fun findByDeviceIdAndToggleType(deviceId: String, toggleType: String): Optional<ToggleState>
    
    fun existsByDeviceIdAndToggleType(deviceId: String, toggleType: String): Boolean
}

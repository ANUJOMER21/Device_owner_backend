package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.DeviceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface DeviceStatusRepository : JpaRepository<DeviceStatus, Long> {
    
    fun findByDeviceId(deviceId: String): Optional<DeviceStatus>
    
    fun findByCustomerId(customerId: String): List<DeviceStatus>
    
    fun findByCustomerIdIn(customerIds: List<String>): List<DeviceStatus>
    
    @Query("""
        SELECT COUNT(DISTINCT ts.device_id) 
        FROM toggle_states ts
        INNER JOIN device_status ds ON ts.device_id = ds.device_id
        WHERE ds.customer_id IN :customerIds 
        AND ts.toggle_type = 'lock' 
        AND ts.state = true
    """, nativeQuery = true)
    fun countLockedDevicesByCustomerIds(@Param("customerIds") customerIds: List<String>): Long
}

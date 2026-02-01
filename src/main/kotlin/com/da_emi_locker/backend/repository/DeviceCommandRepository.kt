package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.CommandStatus
import com.da_emi_locker.backend.entity.DeviceCommand
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional

@Repository
interface DeviceCommandRepository : JpaRepository<DeviceCommand, Long> {
    
    fun findByDeviceId(deviceId: String): List<DeviceCommand>
    
    fun findByDeviceIdAndStatus(deviceId: String, status: CommandStatus): List<DeviceCommand>
    
    fun countByStatus(status: CommandStatus): Long
    
    fun findByStatusOrderByUpdatedAtDesc(status: CommandStatus, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<DeviceCommand>
    
    fun findByCustomerId(customerId: String): List<DeviceCommand>
    
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String): List<DeviceCommand>
    
    fun findByCustomerIdAndStatus(customerId: String, status: CommandStatus): List<DeviceCommand>
    
    override fun findById(id: Long): Optional<DeviceCommand>
    
    @Query("""
        SELECT dc FROM DeviceCommand dc 
        WHERE dc.deviceId = :deviceId 
        AND dc.status IN ('pending', 'sent', 'executing')
        ORDER BY dc.createdAt ASC
    """)
    fun findPendingCommandsByDeviceId(@Param("deviceId") deviceId: String): List<DeviceCommand>
    
    /** Only pending and failed commands are retried. Do not retry 'sent' or 'executing' (FCM already delivered). */
    @Query("""
        SELECT dc FROM DeviceCommand dc 
        WHERE dc.status IN ('pending', 'failed')
        AND (dc.expiryAt IS NULL OR dc.expiryAt > :now)
        AND (dc.lastRetryAt IS NULL OR dc.lastRetryAt < :retryTime)
        AND dc.retryCount < 144
    """)
    fun findCommandsForRetry(@Param("now") now: Instant, @Param("retryTime") retryTime: Instant): List<DeviceCommand>

    /** Commands sent but not yet verified - re-send FCM with 10, 15, 20... min schedule for 24h */
    @Query("""
        SELECT dc FROM DeviceCommand dc 
        WHERE dc.status = 'sent'
        AND dc.executedAt IS NULL
        AND (dc.expiryAt IS NULL OR dc.expiryAt > :now)
        AND ((dc.nextRetryAt IS NULL AND dc.updatedAt < :tenMinAgo) OR (dc.nextRetryAt IS NOT NULL AND dc.nextRetryAt < :now))
    """)
    fun findSentCommandsForResend(@Param("now") now: Instant, @Param("tenMinAgo") tenMinAgo: Instant): List<DeviceCommand>
}

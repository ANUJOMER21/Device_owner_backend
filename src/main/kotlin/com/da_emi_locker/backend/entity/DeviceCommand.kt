package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Device command entity for storing commands sent to devices
 */
@Entity
@Table(name = "device_commands")
class DeviceCommand : BaseEntity() {
    
    @Column(name = "device_id", nullable = false, length = 100)
    var deviceId: String = ""
    
    @Column(name = "customer_id", length = 50)
    var customerId: String? = null
    
    @Column(name = "command_type", nullable = false, length = 50)
    var commandType: String = ""
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "command_data", columnDefinition = "JSONB")
    var commandData: String? = null
    
    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    var status: CommandStatus = CommandStatus.pending
    
    @Column(name = "executed_at")
    var executedAt: Instant? = null
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null
    
    @Column(name = "expiry_at")
    var expiryAt: Instant? = null
    
    @Column(name = "retry_count")
    var retryCount: Int = 0
    
    @Column(name = "last_retry_at")
    var lastRetryAt: Instant? = null
    
    @Column(name = "next_retry_at")
    var nextRetryAt: Instant? = null
}

enum class CommandStatus {
    pending,
    queued,
    sent,
    executing,
    executed,
    failed,
    cancelled,
    expired
}

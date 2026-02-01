package com.da_emi_locker.backend.entity

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import jakarta.persistence.*
import java.time.Instant

/**
 * Activity entity for logging all activities and events
 */
@Entity
@Table(name = "activities")
class Activity : BaseEntity() {
    
    @Column(name = "customer_id", length = 50)
    var customerId: String? = null
    
    @Column(name = "device_id", length = 100)
    var deviceId: String? = null
    
    @Column(name = "activity_type", nullable = false, length = 50)
    var activityType: String = ""
    
    @Column(name = "activity_description", columnDefinition = "TEXT")
    var activityDescription: String? = null
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    var metadata: String? = null
}

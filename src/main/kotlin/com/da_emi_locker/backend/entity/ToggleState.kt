package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Toggle state entity for persistent toggle commands
 */
@Entity
@Table(name = "toggle_states")
class ToggleState : BaseEntity() {
    
    @Column(name = "device_id", nullable = false, length = 100)
    var deviceId: String = ""
    
    @Column(name = "toggle_type", nullable = false, length = 50)
    var toggleType: String = ""
    
    @Column(name = "state")
    var state: Boolean = false
    
    @Column(name = "last_changed_at")
    var lastChangedAt: Instant? = null
    
    @Column(name = "changed_by", length = 50)
    var changedBy: String? = null
}

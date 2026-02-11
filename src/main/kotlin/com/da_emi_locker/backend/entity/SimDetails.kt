package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * SIM details reported by configure app (device). One row per request; latest per customer used for display.
 */
@Entity
@Table(name = "sim_details", indexes = [Index(name = "idx_sim_details_customer_id", columnList = "customer_id")])
class SimDetails : BaseEntity() {

    @Column(name = "customer_id", nullable = false, length = 50)
    var customerId: String = ""

    @Column(name = "device_id", length = 100)
    var deviceId: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sim_data", columnDefinition = "JSONB")
    var simData: String? = null

    /** Extracted phone number for quick change detection */
    @Column(name = "phone_number", length = 20)
    var phoneNumber: String? = null
}

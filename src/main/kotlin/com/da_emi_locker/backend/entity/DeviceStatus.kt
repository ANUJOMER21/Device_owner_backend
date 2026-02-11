package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Device status entity representing IoT device status
 */
@Entity
@Table(name = "device_status")
class DeviceStatus : BaseEntity() {
    
    @Column(name = "device_id", unique = true, nullable = false, length = 100)
    var deviceId: String = ""
    
    @Column(name = "customer_id", nullable = false, length = 50)
    var customerId: String = ""
    
    @Column(name = "device_name", length = 255)
    var deviceName: String? = null
    
    @Column(name = "device_type", length = 50)
    var deviceType: String? = null
    
    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    var status: DeviceStatusEnum = DeviceStatusEnum.offline
    
    @Column(name = "last_seen")
    var lastSeen: Instant? = null
    
    @Column(name = "battery_level")
    var batteryLevel: Int? = null
    
    @Column(name = "signal_strength")
    var signalStrength: Int? = null
    
    @Column(name = "firmware_version", length = 50)
    var firmwareVersion: String? = null
    
    @Column(precision = 10, scale = 8)
    var latitude: java.math.BigDecimal? = null
    
    @Column(precision = 11, scale = 8)
    var longitude: java.math.BigDecimal? = null

    // Phone details collected on activation
    @Column(name = "device_manufacturer", length = 100)
    var deviceManufacturer: String? = null

    @Column(name = "device_model", length = 100)
    var deviceModel: String? = null

    @Column(name = "device_brand", length = 100)
    var deviceBrand: String? = null

    @Column(name = "android_version", length = 20)
    var androidVersion: String? = null

    @Column(name = "sdk_version")
    var sdkVersion: Int? = null

    @Column(name = "serial_number", length = 100)
    var serialNumber: String? = null
}

enum class DeviceStatusEnum {
    online,
    offline,
    maintenance
}

package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "device_owner_config")
class DeviceOwnerConfig {
    @Id
    @Column(name = "id", length = 36)
    var id: String = "default"

    @Column(name = "apk_url", nullable = false, length = 1024)
    var apkUrl: String = ""

    @Column(name = "apk_sha256_base64", nullable = false, length = 128)
    var apkSha256Base64: String = ""

    @Column(name = "updated_at")
    var updatedAt: Instant? = null

    @Column(name = "updated_by", length = 255)
    var updatedBy: String? = null
}

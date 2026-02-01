package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.DeviceOwnerConfig
import org.springframework.data.jpa.repository.JpaRepository

interface DeviceOwnerConfigRepository : JpaRepository<DeviceOwnerConfig, String> {
    fun findByIdOrNull(id: String): DeviceOwnerConfig? = findById(id).orElse(null)
}

package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.DeviceOwnerConfig
import com.da_emi_locker.backend.repository.DeviceOwnerConfigRepository
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import java.util.Base64

@Service
class DeviceOwnerConfigService(
    private val repository: DeviceOwnerConfigRepository,
    private val s3StorageService: S3StorageService
) {
    fun getConfig(): DeviceOwnerConfig? =
        repository.findById("default").orElse(null)

    fun updateConfig(apkUrl: String, apkSha256Base64: String, updatedBy: String?): DeviceOwnerConfig {
        val config = repository.findById("default").orElse(DeviceOwnerConfig().apply { id = "default" })
        config.apkUrl = apkUrl
        config.apkSha256Base64 = apkSha256Base64
        config.updatedAt = java.time.Instant.now()
        config.updatedBy = updatedBy
        return repository.save(config)
    }

    fun uploadApkAndUpdate(file: MultipartFile, updatedBy: String?): DeviceOwnerConfig {
        val url = s3StorageService.uploadApk(file)
            ?: throw IllegalStateException("S3 not configured. Please configure AWS S3 in application properties.")
        val sha256 = computeSha256Base64(file.bytes)
        return updateConfig(url, sha256, updatedBy)
    }

    fun computeSha256Base64(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return Base64.getEncoder().encodeToString(hash)
    }
}

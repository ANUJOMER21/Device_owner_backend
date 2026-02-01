package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.DeviceOwnerConfigService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/admin/device-owner-config")
class AdminDeviceOwnerConfigController(
    private val deviceOwnerConfigService: DeviceOwnerConfigService
) {
    data class ConfigResponse(
        val success: Boolean,
        val message: String,
        val config: ConfigDto? = null
    )

    data class ConfigDto(
        val apkUrl: String,
        val apkSha256Base64: String,
        val updatedAt: String?,
        val updatedBy: String?
    )

    @GetMapping
    fun getConfig(): ResponseEntity<ConfigResponse> {
        val config = deviceOwnerConfigService.getConfig()
            ?: return ResponseEntity.ok(
                ConfigResponse(
                    success = true,
                    message = "No config yet",
                    config = null
                )
            )
        return ResponseEntity.ok(
            ConfigResponse(
                success = true,
                message = "OK",
                config = ConfigDto(
                    apkUrl = config.apkUrl,
                    apkSha256Base64 = config.apkSha256Base64,
                    updatedAt = config.updatedAt?.toString(),
                    updatedBy = config.updatedBy
                )
            )
        )
    }

    @PutMapping
    fun updateConfig(
        @RequestBody body: UpdateConfigRequest,
        @RequestAttribute("adminId") adminId: String?
    ): ResponseEntity<ConfigResponse> {
        val config = deviceOwnerConfigService.updateConfig(
            apkUrl = body.apkUrl,
            apkSha256Base64 = body.apkSha256Base64,
            updatedBy = adminId
        )
        return ResponseEntity.ok(
            ConfigResponse(
                success = true,
                message = "Config updated",
                config = ConfigDto(
                    apkUrl = config.apkUrl,
                    apkSha256Base64 = config.apkSha256Base64,
                    updatedAt = config.updatedAt?.toString(),
                    updatedBy = config.updatedBy
                )
            )
        )
    }

    @PostMapping("/upload-apk")
    fun uploadApk(
        @RequestParam("apk") file: MultipartFile,
        @RequestAttribute("adminId") adminId: String?
    ): ResponseEntity<ConfigResponse> {
        val config = deviceOwnerConfigService.uploadApkAndUpdate(file, adminId)
        return ResponseEntity.ok(
            ConfigResponse(
                success = true,
                message = "APK uploaded and config updated",
                config = ConfigDto(
                    apkUrl = config.apkUrl,
                    apkSha256Base64 = config.apkSha256Base64,
                    updatedAt = config.updatedAt?.toString(),
                    updatedBy = config.updatedBy
                )
            )
        )
    }

    data class UpdateConfigRequest(
        val apkUrl: String,
        val apkSha256Base64: String
    )
}

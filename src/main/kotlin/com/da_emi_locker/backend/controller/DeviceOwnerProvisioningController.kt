package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.DeviceOwnerConfigService
import com.da_emi_locker.backend.service.S3StorageService
import jakarta.validation.constraints.NotBlank
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import java.util.*

@RestController
@RequestMapping("\${device.owner.upload.endpoint:/api/device-owner/provisioning/apk}")
class DeviceOwnerProvisioningController(
    private val s3StorageService: S3StorageService,
    private val deviceOwnerConfigService: DeviceOwnerConfigService,
    @Value("\${DEVICE_OWNER_UPLOAD_API_KEY:}") private val apiKey: String,
    @Value("\${DEVICE_OWNER_PACKAGE_NAME:com.omer.aocdoapp}") private val packageName: String
) {

    data class UploadResponse(
        val id: String,
        val packageName: String,
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val sha256_base64: String
    )

    data class ErrorResponse(
        val message: String
    )

    /**
     * Device Owner APK upload endpoint for provisioning server.
     *
     * Method: POST
     * Content-Type: multipart/form-data
     *
     * Fields:
     * - file: APK binary (application/vnd.android.package-archive)
     * - sha256_hex: SHA-256 of the APK, lowercase hex string
     * - sha256_base64: SHA-256 of the APK, Base64-encoded bytes
     *
     * Optional auth header:
     * Authorization: Bearer <DEVICE_OWNER_UPLOAD_API_KEY>
     */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadDeviceOwnerApk(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("sha256_hex") sha256Hex: String,
        @RequestParam("sha256_base64") sha256Base64: String,
        @RequestHeader("Authorization", required = false) authHeader: String?
    ): ResponseEntity<Any> {
        // Optional API key auth
        if (apiKey.isNotBlank()) {
            val expectedHeader = "Bearer $apiKey"
            if (authHeader == null || authHeader != expectedHeader) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse("Invalid or missing auth token"))
            }
        }

        if (file.isEmpty) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse("Missing APK file"))
        }

        val contentType = file.contentType ?: ""
        if (!contentType.equals("application/vnd.android.package-archive", ignoreCase = true) &&
            !contentType.equals("application/octet-stream", ignoreCase = true)
        ) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse("Invalid content type: expected APK"))
        }

        if (sha256Hex.isBlank() || sha256Base64.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse("Missing SHA-256 fields"))
        }

        val bytes = file.bytes
        val computedHash = MessageDigest.getInstance("SHA-256").digest(bytes)
        val computedHex = computedHash.joinToString("") { "%02x".format(it) }
        val computedBase64 = Base64.getEncoder().encodeToString(computedHash)

        if (!computedHex.equals(sha256Hex.trim(), ignoreCase = true)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse("sha256_hex does not match file contents"))
        }

        if (computedBase64 != sha256Base64.trim()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse("sha256_base64 does not match file contents"))
        }

        return try {
            val url = s3StorageService.uploadApk(file)
                ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse("S3 storage is not configured"))

            // Persist as the current Device Owner config for QR provisioning
            deviceOwnerConfigService.updateConfig(
                apkUrl = url,
                apkSha256Base64 = computedBase64,
                updatedBy = "device-owner-upload-api"
            )

            val id = "do-apk-${System.currentTimeMillis()}"

            val response = UploadResponse(
                id = id,
                packageName = packageName,
                versionCode = 1,
                versionName = "1.0",
                downloadUrl = url,
                sha256_base64 = computedBase64
            )
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse(e.message ?: "Failed to upload APK"))
        }
    }
}


package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.S3StorageService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/admin/uploads")
class AdminS3UploadController(
    private val s3StorageService: S3StorageService
) {
    data class UploadResponse(
        val success: Boolean,
        val message: String,
        val url: String? = null
    )

    @PostMapping("/s3")
    fun uploadToS3(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "prefix", defaultValue = "general") prefix: String
    ): ResponseEntity<UploadResponse> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body(
                UploadResponse(success = false, message = "File is required")
            )
        }
        val url = s3StorageService.uploadImage(file, prefix)
            ?: return ResponseEntity.status(503).body(
                UploadResponse(
                    success = false,
                    message = "S3 not configured. Please configure AWS S3. See AWS_S3_SETUP_GUIDE.md"
                )
            )
        return ResponseEntity.ok(
            UploadResponse(success = true, message = "Uploaded", url = url)
        )
    }
}

package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.S3StorageService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/uploads")
class UploadController(
    private val s3StorageService: S3StorageService
) {

    private val logger = LoggerFactory.getLogger(UploadController::class.java)

    data class UploadResponse(
        val success: Boolean,
        val message: String,
        val url: String? = null
    )

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestParam("file") file: MultipartFile): ResponseEntity<UploadResponse> {
        logger.info("Upload request received, file size: ${file.size}, original name: ${file.originalFilename}")

        if (file.isEmpty) {
            logger.warn("Upload failed: empty file")
            return ResponseEntity.badRequest().body(
                UploadResponse(success = false, message = "File is required")
            )
        }

        if (!s3StorageService.isConfigured()) {
            return ResponseEntity.status(503).body(
                UploadResponse(success = false, message = "S3 storage is not configured. Set AWS_ACCESS_KEY, AWS_SECRET_KEY, and AWS_S3_BUCKET.")
            )
        }

        return try {
            val url = s3StorageService.uploadImage(file, "general")
                ?: return ResponseEntity.status(503).body(
                    UploadResponse(success = false, message = "S3 upload returned no URL.")
                )
            logger.info("File uploaded to S3: $url")
            ResponseEntity.ok(
                UploadResponse(
                    success = true,
                    message = "Uploaded successfully",
                    url = url
                )
            )
        } catch (e: Exception) {
            logger.error("S3 upload failed", e)
            val msg = e.message ?: "Upload failed"
            ResponseEntity.status(503).body(
                UploadResponse(success = false, message = "Upload failed: $msg")
            )
        }
    }
}

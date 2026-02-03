package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.config.S3Config
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.ServerSideEncryption
import java.net.URI
import java.time.Instant
import java.util.UUID

@Service
class S3StorageService(
    private val s3Client: S3Client,
    private val s3Config: S3Config
) {
    private val logger = LoggerFactory.getLogger(S3StorageService::class.java)

    /** Whether S3 bucket and credentials are configured (suitable for uploads). */
    fun isConfigured(): Boolean = s3Config.isConfigured()

    /**
     * Upload file to S3. Returns public URL or null if S3 not configured.
     */
    fun uploadFile(file: MultipartFile, prefix: String): String? {
        if (!isConfigured()) {
            logger.warn("S3 not configured - upload skipped")
            return null
        }
        val ext = file.originalFilename?.substringAfterLast('.', "bin") ?: "bin"
        val key = "$prefix/${Instant.now().epochSecond}_${UUID.randomUUID()}.$ext"
        val request = PutObjectRequest.builder()
            .bucket(s3Config.getBucketName())
            .key(key)
            .contentType(file.contentType ?: "application/octet-stream")
            .serverSideEncryption(ServerSideEncryption.AES256)
            .build()
        s3Client.putObject(request, RequestBody.fromInputStream(file.inputStream, file.size))
        val url = s3Config.getObjectUrl(key)
        logger.info("Uploaded to S3: $key -> $url")
        return url
    }

    /**
     * Upload APK to S3. Returns public URL.
     */
    fun uploadApk(file: MultipartFile): String? =
        uploadFile(file, "device-owner-apk")

    /**
     * Upload image to S3 (customer, aadhar, pan, profile, etc).
     */
    fun uploadImage(file: MultipartFile, prefix: String): String? =
        uploadFile(file, "uploads/$prefix")

    /**
     * Upload wallpaper image to S3. Returns public URL for device to download.
     * Stored under uploads/wallpapers/; delete via deleteObjectByUrl when SET_WALLPAPER is verified.
     */
    fun uploadWallpaper(file: MultipartFile): String? =
        uploadImage(file, "wallpapers")

    /**
     * Delete object from S3 by its public URL (e.g. from getObjectUrl).
     * URL must be for this bucket; key is parsed from path. Returns true if delete was attempted (no-op if S3 not configured).
     */
    fun deleteObjectByUrl(url: String?): Boolean {
        if (url.isNullOrBlank() || !isConfigured()) return false
        val key = try {
            val uri = URI.create(url)
            uri.path.removePrefix("/").takeIf { it.isNotBlank() } ?: return false
        } catch (e: Exception) {
            logger.warn("Invalid wallpaper URL for S3 delete: $url", e)
            return false
        }
        return try {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(key)
                    .build()
            )
            logger.info("Deleted wallpaper from S3: $key")
            true
        } catch (e: S3Exception) {
            logger.warn("S3 delete failed for key $key: {}", e.awsErrorDetails()?.errorMessage() ?: e.message)
            false
        } catch (e: Exception) {
            logger.warn("S3 delete failed for key $key", e)
            false
        }
    }

    /**
     * Verify S3 is configured and bucket is reachable. For use by /api/admin/health/s3.
     */
    fun verifyS3(): Map<String, Any> {
        if (!s3Config.isConfigured()) {
            return mapOf(
                "configured" to false,
                "reachable" to false,
                "bucket" to (s3Config.getBucketName().ifBlank { null } ?: "not set"),
                "region" to s3Config.getRegion(),
                "message" to "S3 not configured: set AWS_S3_BUCKET, AWS_ACCESS_KEY, and AWS_SECRET_KEY (and optionally AWS_S3_REGION)."
            )
        }
        return try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(s3Config.getBucketName()).build())
            mapOf(
                "configured" to true,
                "reachable" to true,
                "bucket" to s3Config.getBucketName(),
                "region" to s3Config.getRegion(),
                "message" to "S3 bucket is reachable."
            )
        } catch (e: S3Exception) {
            logger.warn("S3 verification failed: {}", e.awsErrorDetails()?.errorMessage() ?: e.message)
            mapOf(
                "configured" to true,
                "reachable" to false,
                "bucket" to s3Config.getBucketName(),
                "region" to s3Config.getRegion(),
                "message" to (e.awsErrorDetails()?.errorMessage() ?: e.message ?: "Unknown S3 error"),
                "errorCode" to (e.awsErrorDetails()?.errorCode() ?: e.statusCode().toString())
            )
        } catch (e: Exception) {
            logger.warn("S3 verification failed", e)
            mapOf(
                "configured" to true,
                "reachable" to false,
                "bucket" to s3Config.getBucketName(),
                "region" to s3Config.getRegion(),
                "message" to (e.message ?: "Connection or permission error.")
            )
        }
    }
}

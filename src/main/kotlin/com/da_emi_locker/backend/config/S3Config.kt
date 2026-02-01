package com.da_emi_locker.backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
class S3Config {

    @Value("\${aws.s3.region:ap-south-1}")
    private var region: String = "ap-south-1"

    @Value("\${aws.s3.access-key:}")
    private var accessKey: String = ""

    @Value("\${aws.s3.secret-key:}")
    private var secretKey: String = ""

    @Value("\${aws.s3.bucket-name:}")
    private var bucketName: String = ""

    @Bean
    fun s3Client(): S3Client {
        return if (accessKey.isBlank() || secretKey.isBlank()) {
            S3Client.builder()
                .region(Region.of(region))
                .build()
        } else {
            S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                )
                .build()
        }
    }

    fun getBucketName(): String = bucketName

    fun getRegion(): String = region

    /** True only when bucket and credentials are all set (required for uploads). */
    fun isConfigured(): Boolean =
        bucketName.isNotBlank() && accessKey.isNotBlank() && secretKey.isNotBlank()

    fun getObjectUrl(key: String): String =
        "https://${bucketName}.s3.${region}.amazonaws.com/$key"
}

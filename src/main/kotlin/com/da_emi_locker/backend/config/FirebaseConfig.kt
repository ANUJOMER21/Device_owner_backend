package com.da_emi_locker.backend.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import java.io.FileInputStream
import java.io.InputStream
import javax.annotation.PostConstruct

@Configuration
class FirebaseConfig(
    private val resourceLoader: ResourceLoader
) {
    
    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)
    
    @Value("\${fcm.enabled:false}")
    private var fcmEnabled: Boolean = false
    
    @Value("\${fcm.service-account-key:}")
    private var serviceAccountKeyPath: String = ""
    
    @PostConstruct
    fun initializeFirebase() {
        logger.info("Firebase initialization started. FCM enabled: $fcmEnabled, Key path: $serviceAccountKeyPath")
        
        if (!fcmEnabled) {
            logger.info("FCM is disabled. Skipping Firebase initialization.")
            return
        }
        
        if (serviceAccountKeyPath.isBlank()) {
            logger.warn("FCM service account key path not configured. FCM will not work.")
            return
        }
        
        try {
            // Check if already initialized
            val existingApps = FirebaseApp.getApps()
            if (existingApps.isNotEmpty()) {
                logger.info("Firebase already initialized with ${existingApps.size} app(s)")
                return
            }
            
            logger.info("Initializing Firebase with service account key: $serviceAccountKeyPath")
            
            val inputStream: InputStream = when {
                serviceAccountKeyPath.startsWith("classpath:") -> {
                    logger.info("Loading Firebase config from classpath: $serviceAccountKeyPath")
                    val resource = resourceLoader.getResource(serviceAccountKeyPath)
                    if (!resource.exists()) {
                        throw IllegalStateException("Firebase config file not found at: $serviceAccountKeyPath")
                    }
                    logger.info("Firebase config resource found, loading...")
                    logger.info("Resource URL: ${resource.uri}")
                    resource.inputStream
                }
                serviceAccountKeyPath.startsWith("file:") -> {
                    // Strip "file:" prefix – e.g. file:/app/firebase_config.json -> /app/firebase_config.json
                    val path = serviceAccountKeyPath.removePrefix("file:")
                    logger.info("Loading Firebase config from file: $path")
                    val file = java.io.File(path)
                    if (!file.exists()) {
                        throw IllegalStateException("Firebase config file not found at: $path")
                    }
                    logger.info("File exists, size: ${file.length()} bytes")
                    FileInputStream(file)
                }
                else -> {
                    // Try as file system path first (no prefix)
                    try {
                        logger.info("Trying to load Firebase config from file system: $serviceAccountKeyPath")
                        val file = java.io.File(serviceAccountKeyPath)
                        if (!file.exists()) {
                            throw java.io.FileNotFoundException("File not found: $serviceAccountKeyPath")
                        }
                        logger.info("File exists, size: ${file.length()} bytes")
                        FileInputStream(serviceAccountKeyPath)
                    } catch (e: Exception) {
                        logger.warn("File system path failed, trying classpath: ${e.message}")
                        // If file system path fails, try as classpath resource
                        val resource = resourceLoader.getResource("classpath:${serviceAccountKeyPath}")
                        if (!resource.exists()) {
                            throw IllegalStateException("Firebase config file not found at classpath:${serviceAccountKeyPath}")
                        }
                        logger.info("Classpath resource found: ${resource.uri}")
                        resource.inputStream
                    }
                }
            }
            
            logger.info("Loading Google credentials from input stream...")
            val credentials = inputStream.use { stream ->
                try {
                    GoogleCredentials.fromStream(stream)
                } catch (e: Exception) {
                    logger.error("Failed to load Google credentials: ${e.message}", e)
                    logger.error("Exception details: ${e.javaClass.name}")
                    if (e.cause != null) {
                        logger.error("Caused by: ${e.cause?.javaClass?.name}: ${e.cause?.message}")
                    }
                    throw e
                }
            }
            logger.info("Google credentials loaded successfully")
            
            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build()
            
            logger.info("Initializing Firebase app...")
            FirebaseApp.initializeApp(options)
            logger.info("✅ Firebase initialized successfully!")
            
            // Verify initialization
            val appsAfterInit = FirebaseApp.getApps()
            logger.info("Firebase apps after initialization: ${appsAfterInit.size}")
            if (appsAfterInit.isEmpty()) {
                logger.error("⚠️ Firebase initialization completed but no apps found!")
            }
            
        } catch (e: Exception) {
            logger.error("❌ Failed to initialize Firebase", e)
            logger.error("Exception type: ${e.javaClass.name}")
            logger.error("Exception message: ${e.message}")
            e.printStackTrace()
            // Don't throw exception - allow app to start but FCM won't work
            logger.error("⚠️ Application will continue but FCM notifications will fail")
        }
    }
    
    /**
     * Verify Firebase is initialized and reinitialize if needed
     * Can be called manually or by scheduled task
     */
    fun verifyAndReinitializeFirebase() {
        try {
            val apps = FirebaseApp.getApps()
            if (apps.isEmpty() && fcmEnabled && serviceAccountKeyPath.isNotBlank()) {
                logger.warn("Firebase not initialized but should be. Attempting reinitialization...")
                initializeFirebase()
            } else if (apps.isNotEmpty()) {
                logger.debug("Firebase is initialized with ${apps.size} app(s)")
            }
        } catch (e: Exception) {
            logger.error("Error verifying Firebase initialization", e)
        }
    }
}

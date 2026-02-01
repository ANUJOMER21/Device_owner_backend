package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.config.FirebaseConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class FCMService(
    private val firebaseConfig: FirebaseConfig
) {
    
    private val logger = LoggerFactory.getLogger(FCMService::class.java)
    
    @Value("\${fcm.enabled:false}")
    private var fcmEnabled: Boolean = false
    
    data class FCMNotification(
        val token: String,
        val title: String,
        val body: String,
        val data: Map<String, String> = emptyMap()
    )
    
    data class FCMResponse(
        val success: Boolean,
        val messageId: String? = null,
        val error: String? = null
    )
    
    fun sendNotification(notification: FCMNotification): FCMResponse {
        if (!fcmEnabled) {
            logger.warn("FCM is disabled. Notification not sent.")
            return FCMResponse(
                success = false,
                error = "FCM is disabled"
            )
        }
        
        return try {
            // Ensure Firebase is initialized (attempt reinitialization if needed)
            logger.debug("Verifying Firebase initialization before sending FCM notification")
            firebaseConfig.verifyAndReinitializeFirebase()

            // Check if Firebase is initialized after verification
            val apps = FirebaseApp.getApps()
            if (apps.isEmpty()) {
                logger.error("Firebase is not initialized even after verification. Cannot send FCM notification.")
                logger.error("FCM enabled: $fcmEnabled")
                logger.error("Attempting to check Firebase initialization status...")
                
                // Try to get default app - this will throw if not initialized
                try {
                    FirebaseMessaging.getInstance()
                    logger.warn("FirebaseMessaging.getInstance() succeeded but getApps() is empty - this is unusual")
                } catch (e: IllegalStateException) {
                    logger.error("FirebaseMessaging.getInstance() failed: ${e.message}")
                }
                
                return FCMResponse(
                    success = false,
                    error = "Firebase is not initialized. Check server logs for initialization errors."
                )
            }
            
            logger.debug("Firebase is initialized with ${apps.size} app(s). Sending notification...")
            
            val androidConfig = com.google.firebase.messaging.AndroidConfig.builder()
                .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                .build()

            val message = Message.builder()
                .setToken(notification.token)
                .setAndroidConfig(androidConfig)
                .setNotification(
                    Notification.builder()
                        .setTitle(notification.title)
                        .setBody(notification.body)
                        .build()
                )
                .putAllData(notification.data)
                .build()
            
            val response = FirebaseMessaging.getInstance().send(message)
            
            logger.info("FCM notification sent successfully. Message ID: $response")
            
            FCMResponse(
                success = true,
                messageId = response
            )
        } catch (e: IllegalStateException) {
            logger.error("Firebase not initialized or FCM service unavailable", e)
            FCMResponse(
                success = false,
                error = "Firebase service unavailable: ${e.message}"
            )
        } catch (e: com.google.firebase.messaging.FirebaseMessagingException) {
            // Handle specific FCM errors
            val errorMessage = e.message ?: ""
            val errorCodeStr = try { e.errorCode?.toString() ?: "" } catch (_: Exception) { "" }
            val fullError = "$errorCodeStr $errorMessage".lowercase()
            
            val finalError = when {
                fullError.contains("sender") && (fullError.contains("mismatch") || fullError.contains("invalid")) -> {
                    logger.error("FCM SenderId mismatch - Token was generated with different Firebase project", e)
                    logger.error("Backend Firebase project: aoc-device-control")
                    logger.error("Token likely belongs to a different Firebase project")
                    "FCM failed: SenderId mismatch - The FCM token was generated with a different Firebase project than configured in backend. Backend uses project 'aoc-device-control'. Please ensure mobile app uses the same Firebase project."
                }
                fullError.contains("invalid-registration-token") || 
                fullError.contains("registration-token-not-registered") -> {
                    logger.error("FCM invalid or unregistered token", e)
                    "FCM failed: Invalid or unregistered token - The FCM token is invalid or the app was uninstalled"
                }
                else -> {
                    logger.error("FCM error: $errorCodeStr - $errorMessage", e)
                    "FCM failed: $errorMessage"
                }
            }
            FCMResponse(
                success = false,
                error = finalError
            )
        } catch (e: Exception) {
            logger.error("Failed to send FCM notification", e)
            val errorMsg = e.message ?: "Unknown error"
            // Check if it's a sender ID mismatch in the message
            val finalError = if (errorMsg.contains("sender", ignoreCase = true) && 
                                 errorMsg.contains("mismatch", ignoreCase = true)) {
                logger.error("Detected SenderId mismatch in error message")
                "FCM failed: SenderId mismatch - The FCM token was generated with a different Firebase project than configured in backend. Backend uses project 'aoc-device-control'. Please ensure mobile app uses the same Firebase project."
            } else {
                errorMsg
            }
            FCMResponse(
                success = false,
                error = finalError
            )
        }
    }
    
    fun sendNotificationAsync(notification: FCMNotification): CompletableFuture<FCMResponse> {
        return CompletableFuture.supplyAsync {
            sendNotification(notification)
        }
    }

    /**
     * Sends a data-only FCM message (no visible notification).
     * Use for device commands so the configure app processes them silently in background.
     */
    fun sendDataOnly(token: String, data: Map<String, String>): FCMResponse {
        if (!fcmEnabled) {
            logger.warn("FCM is disabled. Data-only message not sent.")
            return FCMResponse(success = false, error = "FCM is disabled")
        }
        return try {
            firebaseConfig.verifyAndReinitializeFirebase()
            val apps = FirebaseApp.getApps()
            if (apps.isEmpty()) {
                return FCMResponse(success = false, error = "Firebase is not initialized")
            }
            val androidConfig = com.google.firebase.messaging.AndroidConfig.builder()
                .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                .build()
            val message = Message.builder()
                .setToken(token)
                .setAndroidConfig(androidConfig)
                .putAllData(data)
                .build()
            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("FCM data-only message sent. Message ID: $response")
            FCMResponse(success = true, messageId = response)
        } catch (e: Exception) {
            logger.error("Failed to send FCM data-only message", e)
            FCMResponse(success = false, error = e.message ?: "Unknown error")
        }
    }
}

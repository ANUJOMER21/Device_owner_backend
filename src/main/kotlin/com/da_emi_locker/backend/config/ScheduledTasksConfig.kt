package com.da_emi_locker.backend.config

import com.da_emi_locker.backend.service.DeviceCommandService
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Scheduled tasks configuration
 * Handles periodic tasks like retrying failed FCM commands
 */
@Component
@EnableScheduling
class ScheduledTasksConfig(
    private val deviceCommandService: DeviceCommandService,
    private val firebaseConfig: FirebaseConfig
) {
    
    /**
     * Process command queue every hour
     * - Expires commands older than 24 hours
     * - Processes queued commands (checks if device is online)
     * - Retries unverified commands that haven't been verified
     * 
     * This runs every hour to:
     * 1. Check for expired commands (24 hours old) and mark them as cancelled
     * 2. Process queued commands - if device is online, execute immediately
     * 3. Retry unverified commands (sent but not verified) via FCM
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    fun processCommandQueue() {
        deviceCommandService.processCommandQueue()
    }
    
    /**
     * Retry unverified FCM commands every 10 minutes
     * This handles commands that were sent but not verified yet
     * Stops retrying after 24 hours (144 retries * 10 minutes = 24 hours)
     * 
     * Note: This is separate from the hourly queue processor to ensure
     * faster retry for FCM notifications that may have failed to deliver
     */
    @Scheduled(fixedRate = 600000) // 10 minutes in milliseconds
    fun retryUnverifiedCommands() {
        deviceCommandService.retryUnverifiedCommands(Instant.now())
    }
    
    /**
     * Verify Firebase initialization every 5 minutes
     * Reinitializes if Firebase is not initialized but should be
     */
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    fun verifyFirebaseInitialization() {
        firebaseConfig.verifyAndReinitializeFirebase()
    }
}

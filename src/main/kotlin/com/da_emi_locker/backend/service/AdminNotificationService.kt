package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.repository.DealerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminNotificationService(
    private val dealerRepository: DealerRepository,
    private val fcmService: FCMService
) {
    
    private val logger = LoggerFactory.getLogger(AdminNotificationService::class.java)
    
    data class SendNotificationRequest(
        val dealerId: String? = null, // If null, send to all dealers
        val title: String,
        val body: String,
        val data: Map<String, String> = emptyMap()
    )
    
    data class NotificationResponse(
        val success: Boolean,
        val message: String,
        val sentCount: Int = 0,
        val failedCount: Int = 0,
        val results: List<NotificationResult> = emptyList()
    )
    
    data class NotificationResult(
        val dealerId: String,
        val dealerName: String,
        val success: Boolean,
        val messageId: String? = null,
        val error: String? = null
    )
    
    @Transactional
    fun sendNotification(request: SendNotificationRequest): NotificationResponse {
        val dealers = if (request.dealerId != null) {
            val dealer = dealerRepository.findByDealerId(request.dealerId).orElse(null)
            if (dealer != null && dealer.fcmToken != null) {
                listOf(dealer)
            } else {
                emptyList()
            }
        } else {
            // Send to all dealers with FCM tokens
            dealerRepository.findAll().filter { it.fcmToken != null }
        }
        
        if (dealers.isEmpty()) {
            return NotificationResponse(
                success = false,
                message = if (request.dealerId != null) {
                    "Dealer not found or FCM token not set"
                } else {
                    "No dealers with FCM tokens found"
                },
                sentCount = 0,
                failedCount = 0
            )
        }
        
        val results = mutableListOf<NotificationResult>()
        var sentCount = 0
        var failedCount = 0
        
        for (dealer in dealers) {
            if (dealer.fcmToken == null) {
                results.add(
                    NotificationResult(
                        dealerId = dealer.dealerId,
                        dealerName = dealer.name,
                        success = false,
                        error = "FCM token not set"
                    )
                )
                failedCount++
                continue
            }
            
            val fcmResponse = fcmService.sendNotification(
                FCMService.FCMNotification(
                    token = dealer.fcmToken!!,
                    title = request.title,
                    body = request.body,
                    data = request.data
                )
            )
            
            if (fcmResponse.success) {
                results.add(
                    NotificationResult(
                        dealerId = dealer.dealerId,
                        dealerName = dealer.name,
                        success = true,
                        messageId = fcmResponse.messageId
                    )
                )
                sentCount++
            } else {
                results.add(
                    NotificationResult(
                        dealerId = dealer.dealerId,
                        dealerName = dealer.name,
                        success = false,
                        error = fcmResponse.error ?: "Unknown error"
                    )
                )
                failedCount++
            }
        }
        
        return NotificationResponse(
            success = sentCount > 0,
            message = "Sent $sentCount notification(s), $failedCount failed",
            sentCount = sentCount,
            failedCount = failedCount,
            results = results
        )
    }
}

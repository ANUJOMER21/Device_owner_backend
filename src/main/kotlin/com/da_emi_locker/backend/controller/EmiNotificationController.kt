package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.EmiNotificationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * EMI Notification endpoints for dealers.
 *
 * GET  /api/emi-notifications/due-customers        — customers with EMI due recently / upcoming
 * POST /api/emi-notifications/send-reminder         — dealer manually sends EMI reminder to a customer
 * GET  /api/emi-notifications/history               — notification history for dealer's customers
 * GET  /api/emi-notifications/history/{customerId}  — notification history for a specific customer
 */
@RestController
@RequestMapping("/api/emi-notifications")
class EmiNotificationController(
    private val emiNotificationService: EmiNotificationService
) {

    // ── Generic wrapper matching existing API conventions ────────────

    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )

    // ── GET /api/emi-notifications/due-customers ────────────────────

    @GetMapping("/due-customers")
    fun getDueCustomers(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestParam(defaultValue = "7") daysAhead: Int,
        @RequestParam(defaultValue = "7") daysBehind: Int
    ): ResponseEntity<ApiResponse<EmiNotificationService.EmiDueResponse>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized")
            )
        }

        val response = emiNotificationService.getCustomersWithEmiDue(dealerId, daysAhead, daysBehind)
        return ResponseEntity.ok(
            ApiResponse(
                success = response.success,
                message = response.message,
                data = response
            )
        )
    }

    // ── POST /api/emi-notifications/send-reminder ───────────────────

    data class SendReminderRequest(
        val customerId: String,
        val loanId: String? = null
    )

    @PostMapping("/send-reminder")
    fun sendReminder(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestBody request: SendReminderRequest
    ): ResponseEntity<ApiResponse<EmiNotificationService.SendReminderResponse>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized")
            )
        }

        val response = emiNotificationService.sendDealerReminder(
            dealerId = dealerId,
            customerId = request.customerId,
            loanId = request.loanId
        )

        val status = if (response.success) 200 else 400
        return ResponseEntity.status(status).body(
            ApiResponse(
                success = response.success,
                message = response.message,
                data = response
            )
        )
    }

    // ── GET /api/emi-notifications/history ───────────────────────────

    @GetMapping("/history")
    fun getNotificationHistory(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestParam(required = false) customerId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<ApiResponse<EmiNotificationService.NotificationHistoryResponse>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized")
            )
        }

        val response = emiNotificationService.getNotificationHistory(dealerId, customerId, page, pageSize)
        return ResponseEntity.ok(
            ApiResponse(success = true, message = "Notification history retrieved", data = response)
        )
    }

    // ── GET /api/emi-notifications/history/{customerId} ─────────────

    @GetMapping("/history/{customerId}")
    fun getCustomerNotificationHistory(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<ApiResponse<EmiNotificationService.NotificationHistoryResponse>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized")
            )
        }

        val response = emiNotificationService.getNotificationHistory(dealerId, customerId, page, pageSize)
        return ResponseEntity.ok(
            ApiResponse(success = true, message = "Notification history retrieved", data = response)
        )
    }
}

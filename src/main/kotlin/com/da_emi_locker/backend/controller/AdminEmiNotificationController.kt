package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.EmiNotificationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Admin EMI Notification endpoints.
 * All endpoints require ROLE_ADMIN.
 *
 * GET  /api/admin/emi-notifications/due-customers          — all customers with EMI due
 * POST /api/admin/emi-notifications/send-reminder           — send EMI reminder to any customer
 * GET  /api/admin/emi-notifications/history                 — all notification history
 * GET  /api/admin/emi-notifications/history/{customerId}    — history for a customer
 */
@RestController
@RequestMapping("/api/admin/emi-notifications")
class AdminEmiNotificationController(
    private val emiNotificationService: EmiNotificationService
) {

    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )

    // ── GET /api/admin/emi-notifications/due-customers ──────────────

    @GetMapping("/due-customers")
    fun getDueCustomers(
        @RequestParam(required = false) dealerId: String?,
        @RequestParam(defaultValue = "7") daysAhead: Int,
        @RequestParam(defaultValue = "7") daysBehind: Int
    ): ResponseEntity<ApiResponse<EmiNotificationService.EmiDueResponse>> {
        val response = emiNotificationService.getAllCustomersWithEmiDue(dealerId, daysAhead, daysBehind)
        return ResponseEntity.ok(
            ApiResponse(success = response.success, message = response.message, data = response)
        )
    }

    // ── POST /api/admin/emi-notifications/send-reminder ─────────────

    data class AdminSendReminderRequest(
        val customerId: String,
        val loanId: String? = null
    )

    @PostMapping("/send-reminder")
    fun sendReminder(
        @RequestBody request: AdminSendReminderRequest
    ): ResponseEntity<ApiResponse<EmiNotificationService.SendReminderResponse>> {
        val response = emiNotificationService.sendAdminReminder(
            customerId = request.customerId,
            loanId = request.loanId
        )
        val status = if (response.success) 200 else 400
        return ResponseEntity.status(status).body(
            ApiResponse(success = response.success, message = response.message, data = response)
        )
    }

    // ── GET /api/admin/emi-notifications/history ────────────────────

    @GetMapping("/history")
    fun getNotificationHistory(
        @RequestParam(required = false) customerId: String?,
        @RequestParam(required = false) dealerId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<ApiResponse<EmiNotificationService.NotificationHistoryResponse>> {
        val response = emiNotificationService.getAdminNotificationHistory(customerId, dealerId, page, pageSize)
        return ResponseEntity.ok(
            ApiResponse(success = true, message = "Notification history retrieved", data = response)
        )
    }

    // ── GET /api/admin/emi-notifications/history/{customerId} ───────

    @GetMapping("/history/{customerId}")
    fun getCustomerNotificationHistory(
        @PathVariable customerId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<ApiResponse<EmiNotificationService.NotificationHistoryResponse>> {
        val response = emiNotificationService.getAdminNotificationHistory(customerId, null, page, pageSize)
        return ResponseEntity.ok(
            ApiResponse(success = true, message = "Notification history retrieved", data = response)
        )
    }
}

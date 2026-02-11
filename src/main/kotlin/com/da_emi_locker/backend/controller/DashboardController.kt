package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.DashboardService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
    private val dashboardService: DashboardService
) {
    
    // ApiResponse wrapper for dealer app compatibility
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )
    
    // DashboardStatsDto matching dealer app format
    data class DashboardStatsDto(
        val kitRemaining: Int,
        val customerLimit: Int,
        val totalCustomers: Int,
        val pendingActivation: Int,
        val installed: Int,
        val uninstalled: Int,
        val lockedDevices: Int,
        val activeCustomers: Int = 0,
        val inactiveCustomers: Int = 0,
        val pendingEmis: Int = 0,
        val totalRevenue: Double = 0.0
    )
    
    @GetMapping("/stats")
    fun getDashboardStats(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestAttribute(value = "salesExecutiveId", required = false) salesExecutiveId: String?
    ): ResponseEntity<ApiResponse<DashboardStatsDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }

        val response = dashboardService.getDashboardStats(
            dealerId = dealerId,
            salesExecutiveId = salesExecutiveId
        )
        
        return if (response.success && response.stats != null) {
            val s = response.stats
            val statsDto = DashboardStatsDto(
                kitRemaining = s.kitRemaining,
                customerLimit = s.customerLimit,
                totalCustomers = s.totalCustomers.toInt(),
                pendingActivation = s.pendingActivation.toInt(),
                installed = s.installed.toInt(),
                uninstalled = s.uninstalled.toInt(),
                lockedDevices = s.lockedDevices.toInt(),
                activeCustomers = s.activeCustomers.toInt(),
                inactiveCustomers = 0  // Removed with V30 (inactive/blocked status)
            )
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = statsDto
                )
            )
        } else {
            ResponseEntity.status(400).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
    }
}

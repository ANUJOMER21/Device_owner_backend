package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.service.ActivityService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/activities")
class ActivityController(
    private val activityService: ActivityService,
    private val customerRepository: CustomerRepository
) {
    
    // ApiResponse wrapper for dealer app compatibility
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )
    
    // ActivityDto matching dealer app format
    data class ActivityDto(
        val activityId: String,
        val type: String,
        val title: String,
        val description: String,
        val customerName: String?,
        val customerId: String?,
        val timestamp: String,
        val status: String
    )
    
    @GetMapping
    fun getActivities(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestParam(required = false) activityType: String?,
        @RequestParam(required = false) customerId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<List<ActivityDto>>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = activityService.getActivities(dealerId, activityType, customerId, page, size)
        
        return if (response.success && response.activities != null) {
            val activityDtos = response.activities.map { activityData ->
                val customer = activityData.customerId?.let { 
                    customerRepository.findByCustomerId(it).orElse(null)
                }
                ActivityDto(
                    activityId = activityData.id.toString(),
                    type = activityData.activityType,
                    title = activityData.activityType.replace("_", " ").split(" ").joinToString(" ") { 
                        it.capitalize() 
                    },
                    description = activityData.activityDescription ?: "",
                    customerName = customer?.name,
                    customerId = activityData.customerId,
                    timestamp = activityData.createdAt,
                    status = "completed" // Default status
                )
            }
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = activityDtos
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
    
    @GetMapping("/customer/{customerId}")
    fun getCustomerActivities(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<List<ActivityDto>>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = activityService.getCustomerActivities(dealerId, customerId, page, size)
        
        return if (response.success && response.activities != null) {
            val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            val activityDtos = response.activities.map { activityData ->
                ActivityDto(
                    activityId = activityData.id.toString(),
                    type = activityData.activityType,
                    title = activityData.activityType.replace("_", " ").split(" ").joinToString(" ") { 
                        it.capitalize() 
                    },
                    description = activityData.activityDescription ?: "",
                    customerName = customer?.name,
                    customerId = activityData.customerId,
                    timestamp = activityData.createdAt,
                    status = "completed"
                )
            }
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = activityDtos
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
    
    @GetMapping("/{activityId}")
    fun getActivity(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable activityId: Long
    ): ResponseEntity<ApiResponse<ActivityDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = activityService.getActivity(activityId, dealerId)
        
        return if (response.success && response.activity != null) {
            val activityData = response.activity
            val customer = activityData.customerId?.let { 
                customerRepository.findByCustomerId(it).orElse(null)
            }
            val activityDto = ActivityDto(
                activityId = activityData.id.toString(),
                type = activityData.activityType,
                title = activityData.activityType.replace("_", " ").split(" ").joinToString(" ") { 
                    it.capitalize() 
                },
                description = activityData.activityDescription ?: "",
                customerName = customer?.name,
                customerId = activityData.customerId,
                timestamp = activityData.createdAt,
                status = "completed"
            )
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = activityDto
                )
            )
        } else {
            ResponseEntity.status(404).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
    }
}

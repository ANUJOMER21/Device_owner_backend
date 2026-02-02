package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.CommandHistoryService
import com.da_emi_locker.backend.service.CustomerService
import com.da_emi_locker.backend.service.DeviceCommandService
import com.da_emi_locker.backend.service.DeviceStatusService
import com.da_emi_locker.backend.service.SimDetailsService
import com.da_emi_locker.backend.service.ToggleService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/devices")
class DeviceController(
    private val deviceStatusService: DeviceStatusService,
    private val deviceCommandService: DeviceCommandService,
    private val toggleService: ToggleService,
    private val commandHistoryService: CommandHistoryService,
    private val simDetailsService: SimDetailsService,
    private val customerService: CustomerService,
    private val objectMapper: ObjectMapper
) {
    
    data class DeviceActionRequestDto(
        @field:NotBlank(message = "Customer ID is required")
        val customerId: String,
        
        @field:NotBlank(message = "Action is required")
        val action: String,
        
        val reason: String? = null,
        /** Optional payload (e.g. {"package": "com.omer.aocdoapp"} for UNINSTALL_UNBLOCK). */
        val payload: Map<String, String>? = null
    )
    
    @GetMapping("/status/{customerId}")
    fun getDeviceStatus(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String
    ): ResponseEntity<DeviceStatusService.DeviceStatusResponse> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                DeviceStatusService.DeviceStatusResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = deviceStatusService.getDeviceStatusByCustomerId(customerId, dealerId)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(404).body(response)
        }
    }
    
    // ApiResponse wrapper for dealer app compatibility
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )
    
    @PostMapping("/action")
    fun executeDeviceAction(
        @RequestAttribute("dealerId") dealerId: String?,
        @Valid @RequestBody request: DeviceActionRequestDto
    ): ResponseEntity<ApiResponse<Boolean>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = deviceCommandService.executeDeviceAction(
            dealerId,
            DeviceCommandService.DeviceActionRequest(
                customerId = request.customerId,
                action = request.action,
                reason = request.reason,
                payload = request.payload
            )
        )
        
        return if (response.success) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = true
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

    data class MarkUninstalledRequestDto(
        @field:NotBlank(message = "Customer ID is required")
        val customerId: String
    )

    /** Mark customer as uninstalled (no FCM command sent). Disables command section for this customer. */
    @PostMapping("/mark-uninstalled")
    fun markUninstalled(
        @RequestAttribute("dealerId") dealerId: String?,
        @Valid @RequestBody request: MarkUninstalledRequestDto
    ): ResponseEntity<ApiResponse<Boolean>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized")
            )
        }
        val response = customerService.markCustomerUninstalled(request.customerId.trim(), dealerId)
        return if (response.success) {
            ResponseEntity.ok(ApiResponse(success = true, message = response.message, data = true))
        } else {
            ResponseEntity.status(400).body(ApiResponse(success = false, message = response.message))
        }
    }
    
    @GetMapping("/command/{commandId}")
    fun getCommandStatus(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable commandId: Long
    ): ResponseEntity<DeviceCommandService.CommandStatusResponse> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                DeviceCommandService.CommandStatusResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = deviceCommandService.getCommandStatus(commandId, dealerId)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(404).body(response)
        }
    }
    
    @GetMapping("/commands/pending/{customerId}")
    fun getPendingCommands(
        @PathVariable customerId: String
    ): ResponseEntity<Map<String, Any>> {
        val commands = deviceCommandService.getPendingCommands(customerId)
        
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "commands" to commands
        ))
    }
    
    @GetMapping("/sim-details/{customerId}")
    fun getSimDetails(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String
    ): ResponseEntity<Map<String, Any>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(mapOf("success" to false, "message" to "Unauthorized"))
        }
        if (!simDetailsService.customerBelongsToDealer(customerId, dealerId)) {
            return ResponseEntity.status(403).body(mapOf("success" to false, "message" to "Access denied"))
        }
        val sim = simDetailsService.getLatestByCustomerId(customerId)
        return if (sim != null) {
            val rawSimData = sim.simData ?: "{}"
            val msisdn = try {
                val node = objectMapper.readTree(rawSimData)
                node.path("msisdn")
                    .takeIf { !it.isMissingNode && !it.isNull }
                    ?.asText()
                    ?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }

            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    // Raw SIM JSON as reported by device (includes imei, msisdn, operator, networkOperator, etc.)
                    "simData" to rawSimData,
                    // Explicit mobile number field for dealer UI convenience; may be empty if not available
                    "msisdn" to (msisdn ?: ""),
                    "createdAt" to (sim.createdAt?.toString() ?: "")
                )
            )
        } else {
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "No SIM details yet",
                    "simData" to "{}",
                    "msisdn" to ""
                )
            )
        }
    }
    
    // ToggleCommandResponseDto matching dealer app format
    data class ToggleCommandResponseDto(
        val toggleId: String,
        val customerId: String,
        val toggleType: String,
        val enabled: Boolean,
        val status: String,
        val queuedAt: String
    )
    
    // ToggleStateDto matching dealer app format
    data class ToggleStateDto(
        val toggleType: String,
        val enabled: Boolean,
        val lastUpdated: String,
        val status: String
    )
    
    // CustomerToggleStatesDto matching dealer app format
    data class CustomerToggleStatesDto(
        val customerId: String,
        val toggles: List<ToggleStateDto>
    )
    
    @PostMapping("/toggle")
    fun setToggle(
        @RequestAttribute("dealerId") dealerId: String?,
        @Valid @RequestBody request: ToggleService.ToggleRequest
    ): ResponseEntity<ApiResponse<ToggleCommandResponseDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = toggleService.setToggle(dealerId, request)
        
        return if (response.success && response.toggleState != null) {
            val toggleDto = ToggleCommandResponseDto(
                toggleId = java.util.UUID.randomUUID().toString(), // Generate toggle ID
                customerId = request.customerId,
                toggleType = response.toggleState.toggleType,
                enabled = response.toggleState.state,
                status = "queued",
                queuedAt = response.toggleState.lastChangedAt ?: java.time.Instant.now().toString()
            )
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = toggleDto
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
    
    @GetMapping("/toggles/{customerId}")
    fun getToggles(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String
    ): ResponseEntity<ApiResponse<CustomerToggleStatesDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = toggleService.getToggles(customerId, dealerId)
        
        return if (response.success && response.toggles != null) {
            val toggleDtos = response.toggles.map { (toggleType, enabled) ->
                ToggleStateDto(
                    toggleType = toggleType,
                    enabled = enabled,
                    lastUpdated = java.time.Instant.now().toString(), // TODO: Get actual last updated time
                    status = if (enabled) "enabled" else "disabled"
                )
            }
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = CustomerToggleStatesDto(
                        customerId = customerId,
                        toggles = toggleDtos
                    )
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
    
    @GetMapping("/toggle/{customerId}/{toggleType}")
    fun getToggleState(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String,
        @PathVariable toggleType: String
    ): ResponseEntity<ApiResponse<ToggleStateDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = toggleService.getToggles(customerId, dealerId)
        
        return if (response.success && response.toggles != null) {
            val enabled = response.toggles[toggleType] ?: false
            val toggleDto = ToggleStateDto(
                toggleType = toggleType,
                enabled = enabled,
                lastUpdated = java.time.Instant.now().toString(),
                status = if (enabled) "enabled" else "disabled"
            )
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = "Toggle state retrieved successfully",
                    data = toggleDto
                )
            )
        } else {
            ResponseEntity.status(404).body(
                ApiResponse(
                    success = false,
                    message = response.message ?: "Toggle state not found"
                )
            )
        }
    }
    
    @GetMapping("/commands/history")
    fun getCommandHistory(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestParam(required = false) customerId: String?,
        @RequestParam(required = false) commandType: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<CommandHistoryService.CommandHistoryResponse> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                CommandHistoryService.CommandHistoryResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = commandHistoryService.getCommandHistory(
            dealerId,
            customerId,
            commandType,
            status,
            page,
            size
        )
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    @GetMapping("/commands/history/customer/{customerId}")
    fun getCommandHistoryByCustomer(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<CommandHistoryService.CommandHistoryResponse> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                CommandHistoryService.CommandHistoryResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = commandHistoryService.getCommandHistoryByCustomer(
            dealerId,
            customerId,
            page,
            size
        )
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
}

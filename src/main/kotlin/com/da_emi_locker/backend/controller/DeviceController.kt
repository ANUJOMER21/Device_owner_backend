package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.CommandHistoryService
import com.da_emi_locker.backend.service.CustomerService
import com.da_emi_locker.backend.service.DeviceCommandService
import com.da_emi_locker.backend.service.DeviceStatusService
import com.da_emi_locker.backend.service.S3StorageService
import com.da_emi_locker.backend.service.SimDetailsService
import com.da_emi_locker.backend.service.ToggleService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/devices")
class DeviceController(
    private val deviceStatusService: DeviceStatusService,
    private val deviceCommandService: DeviceCommandService,
    private val toggleService: ToggleService,
    private val commandHistoryService: CommandHistoryService,
    private val simDetailsService: SimDetailsService,
    private val customerService: CustomerService,
    private val s3StorageService: S3StorageService,
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
    
    /** Wallpaper upload: dealer sends image; stored in S3; returns public URL for SET_WALLPAPER command. */
    @PostMapping(value = ["/wallpaper"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadWallpaper(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestParam customerId: String,
        @RequestParam image: MultipartFile
    ): ResponseEntity<ApiResponse<Map<String, String>?>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized", data = null)
            )
        }
        val cid = customerId.trim()
        if (cid.isBlank()) {
            return ResponseEntity.status(400).body(
                ApiResponse(success = false, message = "customerId is required", data = null)
            )
        }
        if (!simDetailsService.customerBelongsToDealer(cid, dealerId)) {
            return ResponseEntity.status(403).body(
                ApiResponse(success = false, message = "Access denied to this customer", data = null)
            )
        }
        val contentType = image.contentType?.lowercase() ?: ""
        if (image.isEmpty) {
            return ResponseEntity.status(400).body(
                ApiResponse(success = false, message = "image file is required", data = null)
            )
        }
        if (!contentType.contains("jpeg") && !contentType.contains("jpg") && !contentType.contains("png")) {
            return ResponseEntity.status(400).body(
                ApiResponse(success = false, message = "image must be JPEG or PNG", data = null)
            )
        }
        if (!s3StorageService.isConfigured()) {
            return ResponseEntity.status(503).body(
                ApiResponse(success = false, message = "S3 storage is not configured", data = null)
            )
        }
        val url = s3StorageService.uploadWallpaper(image)
            ?: return ResponseEntity.status(500).body(
                ApiResponse(success = false, message = "Upload failed", data = null)
            )
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Wallpaper uploaded successfully",
                data = mapOf("url" to url)
            )
        )
    }

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
    
    /** Get SIM change history for a customer */
    @GetMapping("/sim-history/{customerId}")
    fun getSimHistory(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String
    ): ResponseEntity<Map<String, Any>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(mapOf("success" to false, "message" to "Unauthorized"))
        }
        val history = simDetailsService.getHistoryForDealer(customerId, dealerId)
            ?: return ResponseEntity.status(403).body(mapOf("success" to false, "message" to "Access denied"))
        
        val historyData = history.map { sim ->
            mapOf(
                "id" to (sim.id ?: 0),
                "simData" to (sim.simData ?: "{}"),
                "phoneNumber" to (sim.phoneNumber ?: ""),
                "createdAt" to (sim.createdAt?.toString() ?: ""),
                "updatedAt" to (sim.updatedAt?.toString() ?: "")
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "history" to historyData))
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

    // ── Offline Lock/Unlock via SMS ─────────────────────────────────────────

    data class SmsCommandRequestDto(
        @field:NotBlank(message = "Customer ID is required")
        val customerId: String = "",
        @field:NotBlank(message = "Action is required (LOCK or UNLOCK)")
        val action: String = "" // "LOCK" or "UNLOCK"
    )

    data class SmsCommandResponseDto(
        val success: Boolean,
        val message: String,
        val smsMessage: String? = null,
        val customerPhone: String? = null
    )

    /**
     * Generate an encrypted SMS command for offline lock/unlock.
     * Dealer sends this SMS from their phone to the customer's device.
     * The configure app has an SMS receiver that decrypts and executes the command.
     * Returns the encrypted SMS text that the dealer should send.
     */
    @PostMapping("/offline-sms-command")
    fun generateOfflineSmsCommand(
        @RequestAttribute("dealerId") dealerId: String?,
        @Valid @RequestBody request: SmsCommandRequestDto
    ): ResponseEntity<SmsCommandResponseDto> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                SmsCommandResponseDto(success = false, message = "Unauthorized")
            )
        }
        val customer = customerService.getCustomer(request.customerId.trim(), dealerId)
        if (!customer.success || customer.customer == null) {
            return ResponseEntity.status(400).body(
                SmsCommandResponseDto(success = false, message = customer.message)
            )
        }
        val cust = customer.customer!!
        val smsSecretKey = cust.smsSecretKey
        if (smsSecretKey.isNullOrBlank()) {
            return ResponseEntity.status(400).body(
                SmsCommandResponseDto(success = false, message = "SMS secret key not configured for this customer. Device must be activated first.")
            )
        }
        val action = request.action.uppercase().trim()
        if (action != "LOCK" && action != "UNLOCK") {
            return ResponseEntity.status(400).body(
                SmsCommandResponseDto(success = false, message = "Action must be LOCK or UNLOCK")
            )
        }
        // Generate encrypted SMS message: DAEMI:<action>:<timestamp>:<hmac>
        val timestamp = System.currentTimeMillis().toString()
        val dataToSign = "$action:$timestamp"
        val hmac = generateHmac(dataToSign, smsSecretKey)
        val smsMessage = "DAEMI:$action:$timestamp:$hmac"

        // Get the customer's phone number for the dealer to send SMS to
        val simDetails = simDetailsService.getLatestForDealer(request.customerId.trim(), dealerId)
        val customerPhone = if (simDetails?.phoneNumber?.isNotBlank() == true) {
            simDetails.phoneNumber
        } else {
            cust.phone
        }

        return ResponseEntity.ok(
            SmsCommandResponseDto(
                success = true,
                message = "SMS command generated. Send this message to the customer's phone number.",
                smsMessage = smsMessage,
                customerPhone = customerPhone
            )
        )
    }

    private fun generateHmac(data: String, key: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }
}

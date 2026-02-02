package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.Activity
import com.da_emi_locker.backend.entity.CommandStatus
import com.da_emi_locker.backend.entity.Customer
import com.da_emi_locker.backend.entity.CustomerStatus
import com.da_emi_locker.backend.entity.DeviceCommand
import com.da_emi_locker.backend.entity.DeviceStatusEnum
import com.da_emi_locker.backend.repository.ActivityRepository
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DeviceCommandRepository
import com.da_emi_locker.backend.repository.DeviceStatusRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import org.slf4j.LoggerFactory

@Service
class DeviceCommandService(
    private val deviceCommandRepository: DeviceCommandRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val customerRepository: CustomerRepository,
    private val activityRepository: ActivityRepository,
    private val fcmService: FCMService,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper
) {
    
    private val logger = LoggerFactory.getLogger(DeviceCommandService::class.java)

    /**
     * Central enum for all supported FCM device commands.
     * The [value] is what we persist in `device_commands.command_type`
     * and what the mobile app will interpret.
     */
    enum class CommandAction(val value: String, val requiresPayload: Boolean = false) {
        // Device Lock & Task Management
        LOCK_TASK("LOCK_TASK"),
        UNLOCK_TASK("UNLOCK_TASK"),
        LOCK_DEVICE("LOCK_DEVICE"),
        REBOOT("REBOOT"),
        
        // USB Management
        USB_BLOCK("USB_BLOCK"),
        USB_UNBLOCK("USB_UNBLOCK"),
        
        // Camera Control
        CAMERA_BLOCK("CAMERA_BLOCK"),
        CAMERA_UNBLOCK("CAMERA_UNBLOCK"),
        
        // Factory Reset
        FACTORY_RESET_BLOCK("FACTORY_RESET_BLOCK"),
        FACTORY_RESET_UNBLOCK("FACTORY_RESET_UNBLOCK"),
        
        // Status Bar
        STATUS_BAR_BLOCK("STATUS_BAR_BLOCK"),
        
        // Wallpaper (requires url payload)
        SET_WALLPAPER("SET_WALLPAPER", requiresPayload = true),
        
        // App Visibility
        // For HIDE_APPS/UNHIDE_APPS, payload is now optional:
        // - If payload.packages present => hide/unhide specific apps
        // - If payload is empty/omitted => device auto-hides/unhides all user apps
        HIDE_APPS("HIDE_APPS"),
        UNHIDE_APPS("UNHIDE_APPS"),
        HIDE_DO_APP("HIDE_DO_APP"),
        UNHIDE_DO_APP("UNHIDE_DO_APP"),
        SUSPEND_DO_APP("SUSPEND_DO_APP"),
        UNSUSPEND_DO_APP("UNSUSPEND_DO_APP"),
        
        // Install/Uninstall Management
        INSTALL_BLOCK("INSTALL_BLOCK"),
        INSTALL_UNBLOCK("INSTALL_UNBLOCK"),
        UNINSTALL_BLOCK("UNINSTALL_BLOCK"), // Can have optional payload
        UNINSTALL_UNBLOCK("UNINSTALL_UNBLOCK"), // Can have optional payload
        
        // Unknown Sources
        INSTALL_FROM_UNKNOWN_SOURCES_BLOCK("INSTALL_FROM_UNKNOWN_SOURCES_BLOCK"),
        INSTALL_FROM_UNKNOWN_SOURCES_UNBLOCK("INSTALL_FROM_UNKNOWN_SOURCES_UNBLOCK"),
        
        // App Suspend/Unsuspend
        APP_SUSPEND("APP_SUSPEND", requiresPayload = true),
        APP_UNSUSPEND("APP_UNSUSPEND", requiresPayload = true),
        
        // App Enable/Disable
        APP_ENABLE("APP_ENABLE", requiresPayload = true),
        APP_DISABLE("APP_DISABLE", requiresPayload = true),
        
        // Background Data Restrictions
        BACKGROUND_DATA_RESTRICT("BACKGROUND_DATA_RESTRICT", requiresPayload = true),
        BACKGROUND_DATA_UNRESTRICT("BACKGROUND_DATA_UNRESTRICT", requiresPayload = true),
        
        // Default App Management
        DEFAULT_APP_SET("DEFAULT_APP_SET", requiresPayload = true),
        DEFAULT_APP_CLEAROUT("DEFAULT_APP_CLEAROUT", requiresPayload = true),
        
        // Outgoing Calls
        OUTGOING_CALLS_BLOCK("OUTGOING_CALLS_BLOCK"),
        OUTGOING_CALLS_UNBLOCK("OUTGOING_CALLS_UNBLOCK"),
        
        // Text to Speech
        SPEAK("SPEAK", requiresPayload = true),
        // EMI Alert: notification + optional TTS (custom or preset text)
        EMI_ALERT("EMI_ALERT", requiresPayload = true),
        
        // Configure app: remove device owner, restart location service, update FCM token, refresh/relaunch
        REMOVE_DEVICE_OWNER("REMOVE_DEVICE_OWNER"),
        RESTART_LOCATION_SERVICE("RESTART_LOCATION_SERVICE"),
        UPDATE_FCM_TOKEN("UPDATE_FCM_TOKEN"),
        REFRESH("REFRESH"),
        
        // Reboot schedules
        REBOOT_12HR("REBOOT_12HR"),
        REBOOT_24HR("REBOOT_24HR"),
        
        // Wallpaper restrict (toggle: customer cannot change wallpaper)
        RESTRICT_WALLPAPER("RESTRICT_WALLPAPER"),
        RESTRICT_WALLPAPER_UNBLOCK("RESTRICT_WALLPAPER_UNBLOCK"),
        
        // Location (enable-disable toggle)
        LOCATION_ENABLE("LOCATION_ENABLE"),
        LOCATION_DISABLE("LOCATION_DISABLE"),
        
        // Download block (toggle)
        DOWNLOAD_BLOCK("DOWNLOAD_BLOCK"),
        DOWNLOAD_UNBLOCK("DOWNLOAD_UNBLOCK"),
        
        // Device owner password
        RESET_PASSWORD("RESET_PASSWORD", requiresPayload = true),
        REMOVE_PASSWORD("REMOVE_PASSWORD"),
        
        // Legacy/compatibility
        UNLOCK_DEVICE("UNLOCK_DEVICE"),
        TOGGLE("TOGGLE"),
        FETCH_LATEST_LOCATION("FETCH_LATEST_LOCATION"),
        GET_SIM_DETAILS("GET_SIM_DETAILS");

        companion object {
            /**
             * Accepts both enum-like names and wire values.
             */
            fun fromRaw(raw: String): CommandAction? {
                val normalized = raw.uppercase().replace("-", "_")
                return values().firstOrNull { 
                    it.name == normalized || it.value.uppercase() == normalized 
                }
            }
        }
    }

    data class DeviceActionRequest(
        val customerId: String,
        val action: String, // e.g. "LOCK_DEVICE", "REMOVE_DEVICE_OWNER", "UNINSTALL_UNBLOCK"
        val reason: String? = null,
        /** Optional payload (e.g. package name for UNINSTALL_UNBLOCK). */
        val payload: Map<String, String>? = null
    )
    
    data class DeviceActionResponse(
        val success: Boolean,
        val message: String,
        val commandId: Long? = null,
        val status: String? = null
    )
    
    data class CommandStatusResponse(
        val success: Boolean,
        val message: String,
        val command: CommandData? = null
    )
    
    data class CommandData(
        val commandId: Long,
        val deviceId: String,
        val commandType: String,
        val commandData: String?,
        val status: String,
        val executedAt: String?,
        val errorMessage: String?,
        val retryCount: Int,
        val createdAt: String
    )
    
    data class VerifyCommandRequest(
        val commandId: Long,
        val success: Boolean,
        val errorMessage: String? = null
    )
    
    data class VerifyCommandResponse(
        val success: Boolean,
        val message: String
    )
    
    @Transactional
    fun executeDeviceAction(dealerId: String, request: DeviceActionRequest): DeviceActionResponse {
        // Verify customer belongs to dealer
        val customer = customerRepository.findByCustomerId(request.customerId).orElse(null)
            ?: return DeviceActionResponse(success = false, message = "Customer not found")
        
        if (customer.dealerId != dealerId) {
            return DeviceActionResponse(
                success = false,
                message = "Access denied"
            )
        }

        return executeDeviceActionInternal(
            customer = customer,
            actionRaw = request.action,
            reason = request.reason,
            payload = request.payload,
            activityCustomerId = request.customerId
        )
    }

    /**
     * Admin-only execution path (no dealer/customer ownership checks).
     * This is intentionally "no security" as requested.
     */
    fun executeDeviceActionAsAdmin(request: DeviceActionRequest): DeviceActionResponse {
        return try {
            val customer = customerRepository.findByCustomerId(request.customerId).orElse(null)
                ?: return DeviceActionResponse(success = false, message = "Customer not found")

            executeDeviceActionInternal(
                customer = customer,
                actionRaw = request.action,
                reason = request.reason,
                payload = request.payload,
                activityCustomerId = request.customerId
            )
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(DeviceCommandService::class.java)
                .error("Error executing device action as admin for customer ${request.customerId}", e)
            DeviceActionResponse(
                success = false,
                message = "Failed to execute device action: ${e.message ?: "Unknown error"}"
            )
        }
    }

    private fun executeDeviceActionInternal(
        customer: Customer,
        actionRaw: String,
        reason: String?,
        payload: Map<String, String>?,
        activityCustomerId: String
    ): DeviceActionResponse {
        return try {
            // Map raw action string to enum
            val actionEnum = CommandAction.fromRaw(actionRaw)
                ?: return DeviceActionResponse(
                    success = false,
                    message = "Invalid action: $actionRaw"
                )

            // Validate payload presence for commands that require it
            if (actionEnum.requiresPayload && reason.isNullOrBlank() && (payload == null || payload.isEmpty())) {
                return DeviceActionResponse(
                    success = false,
                    message = "Payload is required for action ${actionEnum.value}"
                )
            }

            // Get device for customer
            val device = deviceStatusRepository.findByCustomerId(customer.customerId).firstOrNull()
                ?: return DeviceActionResponse(success = false, message = "No device found for customer")

            // Create and save command - build commandData from payload map or reason string
            val commandDataJson: String? = when {
                !payload.isNullOrEmpty() -> objectMapper.writeValueAsString(payload)
                !reason.isNullOrBlank() -> {
                    try {
                        objectMapper.readTree(reason)
                        reason
                    } catch (e: Exception) {
                        objectMapper.writeValueAsString(reason)
                    }
                }
                actionEnum == CommandAction.UNINSTALL_UNBLOCK -> objectMapper.writeValueAsString(mapOf("package" to "com.omer.aocdoapp"))
                else -> null
            }
            
            val command = DeviceCommand().apply {
                this.deviceId = device.deviceId
                this.customerId = customer.customerId  // Link command to customer
                this.commandType = actionEnum.value
                this.commandData = commandDataJson
                this.status = CommandStatus.pending  // Status: pending (will be sent later via separate service)
                this.expiryAt = Instant.now().plus(24, ChronoUnit.HOURS)
                this.retryCount = 0
                this.nextRetryAt = null
                this.createdAt = Instant.now()
                this.updatedAt = Instant.now()
            }

            val savedCommand = deviceCommandRepository.save(command)

            // Update customer status only for unlock (restore to active)
            // IMPORTANT: Do NOT mark as uninstalled for REMOVE_DEVICE_OWNER - wait for device to call /deregister
            if (actionEnum == CommandAction.UNLOCK_DEVICE || actionEnum == CommandAction.UNLOCK_TASK) {
                customer.status = CustomerStatus.active
                customerRepository.save(customer)
            }
            // Explicitly do NOT mark as uninstalled for REMOVE_DEVICE_OWNER - device must verify via /deregister endpoint

            // Log activity with actual command name
            val activity = Activity().apply {
                this.customerId = activityCustomerId
                this.deviceId = device.deviceId
                this.activityType = "command_created"
                this.activityDescription = "Command '${actionEnum.value}' created and sent to device"
                this.createdAt = Instant.now()
            }
            activityRepository.save(activity)

            // Process command immediately in background (check device online status and send FCM)
            // Run asynchronously so API response is not blocked, but process immediately
            logger.info("🚀 Triggering immediate background FCM processing for command ${savedCommand.id}")
            CompletableFuture.runAsync {
                try {
                    processCommandImmediately(savedCommand, customer, device)
                } catch (e: Exception) {
                    logger.error("Error in async FCM processing for command ${savedCommand.id}", e)
                }
            }

            // Return response immediately - status will be updated by background task
            // Note: Status may still show 'pending' initially, but will be updated to 'sent'/'failed'/'queued' within seconds
            DeviceActionResponse(
                success = true,
                message = "Command created successfully and FCM processing started immediately",
                commandId = savedCommand.id,
                status = savedCommand.status.name
            )
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(DeviceCommandService::class.java)
                .error("Error executing device action internal for customer ${customer.customerId}", e)
            DeviceActionResponse(
                success = false,
                message = "Failed to execute device action: ${e.message ?: "Unknown error"}"
            )
        }
    }
    
    fun getCommandStatus(commandId: Long, dealerId: String): CommandStatusResponse {
        val command = deviceCommandRepository.findById(commandId)
            .orElse(null) ?: return CommandStatusResponse(
                success = false,
                message = "Command not found"
            )
        
        // Verify command belongs to dealer's customer
        val customer = customerRepository.findByCustomerId(
            deviceStatusRepository.findByDeviceId(command.deviceId)
                .orElse(null)?.customerId ?: return CommandStatusResponse(
                    success = false,
                    message = "Device not found"
                )
        ).orElse(null) ?: return CommandStatusResponse(
            success = false,
            message = "Customer not found"
        )
        
        if (customer.dealerId != dealerId) {
            return CommandStatusResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        return CommandStatusResponse(
            success = true,
            message = "Command status retrieved successfully",
            command = CommandData(
                commandId = command.id ?: 0,
                deviceId = command.deviceId,
                commandType = command.commandType,
                commandData = command.commandData,
                status = command.status.name,
                executedAt = command.executedAt?.toString(),
                errorMessage = command.errorMessage,
                retryCount = command.retryCount,
                createdAt = command.createdAt?.toString() ?: ""
            )
        )
    }
    
    fun getPendingCommands(customerId: String): List<CommandData> {
        val devices = deviceStatusRepository.findByCustomerId(customerId)
        if (devices.isEmpty()) {
            return emptyList()
        }
        
        val deviceId = devices.first().deviceId
        val commands = deviceCommandRepository.findPendingCommandsByDeviceId(deviceId)
        
        return commands.map { command ->
            CommandData(
                commandId = command.id ?: 0,
                deviceId = command.deviceId,
                commandType = command.commandType,
                commandData = command.commandData,
                status = command.status.name,
                executedAt = command.executedAt?.toString(),
                errorMessage = command.errorMessage,
                retryCount = command.retryCount,
                createdAt = command.createdAt?.toString() ?: ""
            )
        }
    }
    
    @Transactional
    fun verifyCommandExecution(request: VerifyCommandRequest): VerifyCommandResponse {
        val command = deviceCommandRepository.findById(request.commandId)
            .orElse(null) ?: return VerifyCommandResponse(
                success = false,
                message = "Command not found"
            )
        
        if (command.status == CommandStatus.executed) {
            return VerifyCommandResponse(
                success = true,
                message = "Command already executed"
            )
        }
        
        if (request.success) {
            command.status = CommandStatus.executed
            command.executedAt = Instant.now()
            command.errorMessage = null
        } else {
            command.status = CommandStatus.failed
            command.errorMessage = request.errorMessage
        }
        
        command.updatedAt = Instant.now()
        deviceCommandRepository.save(command)
        
        // Handle REMOVE_DEVICE_OWNER command verification - mark as uninstalled only when device confirms
        val customerId = command.customerId // Store in local variable to avoid smart cast issues
        if (request.success && command.commandType == "REMOVE_DEVICE_OWNER" && customerId != null) {
            val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            if (customer != null && customer.status != CustomerStatus.uninstalled) {
                customer.status = CustomerStatus.uninstalled
                customer.imei1 = null
                customer.imei2 = null
                customer.fcmToken = null
                customer.updatedAt = Instant.now()
                customerRepository.save(customer)
                
                // Remove device_status rows for this customer
                val deviceStatuses = deviceStatusRepository.findByCustomerId(customerId)
                if (deviceStatuses.isNotEmpty()) {
                    deviceStatusRepository.deleteAll(deviceStatuses)
                }
                
                // Log uninstall activity
                val uninstallActivity = Activity().apply {
                    this.customerId = customerId // Use local variable
                    this.deviceId = command.deviceId
                    this.activityType = "device_uninstalled"
                    this.activityDescription = "Device uninstalled - REMOVE_DEVICE_OWNER command verified by device"
                    this.createdAt = Instant.now()
                }
                activityRepository.save(uninstallActivity)
            }
        }
        
        // Log activity with command name and customerId
        val activityCustomerId = command.customerId // Store in local variable to avoid smart cast issues
        val activity = Activity().apply {
            this.customerId = activityCustomerId
            this.deviceId = command.deviceId
            this.activityType = if (request.success) "command_executed" else "command_failed"
            this.activityDescription = "Command '${command.commandType}' ${if (request.success) "executed successfully" else "failed: ${request.errorMessage ?: "Unknown error"}"}"
            this.createdAt = Instant.now()
        }
        activityRepository.save(activity)
        
        return VerifyCommandResponse(
            success = true,
            message = "Command verification recorded"
        )
    }
    
    private data class FcmSendResult(val success: Boolean, val error: String? = null)

    /**
     * Send a device command directly to an FCM token (admin only).
     * No customer/device record required. Useful for testing or sending to a device by token.
     */
    data class SendCommandByTokenRequest(
        val fcmToken: String,
        val commandType: String,
        val commandData: String? = null
    )

    data class SendCommandByTokenResponse(
        val success: Boolean,
        val message: String,
        val messageId: String? = null,
        val error: String? = null
    )

    fun sendCommandByFcmToken(request: SendCommandByTokenRequest): SendCommandByTokenResponse {
        val token = request.fcmToken?.trim().orEmpty()
        if (token.isBlank()) {
            return SendCommandByTokenResponse(success = false, message = "FCM token is required")
        }
        val actionEnum = CommandAction.fromRaw(request.commandType)
            ?: return SendCommandByTokenResponse(
                success = false,
                message = "Invalid command type: ${request.commandType}"
            )
        if (actionEnum.requiresPayload && request.commandData.isNullOrBlank()) {
            return SendCommandByTokenResponse(
                success = false,
                message = "Payload is required for command ${actionEnum.value}"
            )
        }
        return try {
            val payloadData: com.fasterxml.jackson.databind.JsonNode = run {
                val data = request.commandData?.trim().orEmpty()
                if (data.isBlank()) {
                    objectMapper.createObjectNode()
                } else {
                    val parsed = try {
                        objectMapper.readTree(data)
                    } catch (_: Exception) {
                        null
                    }
                    when {
                        parsed != null -> parsed
                        else -> objectMapper.createObjectNode().put("data", data)
                    }
                }
            }
            val commandId = "token-${System.currentTimeMillis()}"
            val fcmPayload = objectMapper.createObjectNode().apply {
                put("command_id", commandId)
                put("command", actionEnum.value)
                set<com.fasterxml.jackson.databind.JsonNode>("payload", payloadData)
            }
            val fcmPayloadString = objectMapper.writeValueAsString(fcmPayload)
            val response = fcmService.sendDataOnly(token, mapOf("payload" to fcmPayloadString))
            if (response.success) {
                logger.info("Command sent by FCM token successfully. MessageId: ${response.messageId}")
                SendCommandByTokenResponse(
                    success = true,
                    message = "Command sent successfully to device",
                    messageId = response.messageId
                )
            } else {
                SendCommandByTokenResponse(
                    success = false,
                    message = "FCM send failed",
                    error = response.error
                )
            }
        } catch (e: Exception) {
            logger.error("Exception sending command by FCM token: ${e.message}", e)
            SendCommandByTokenResponse(
                success = false,
                message = "Failed to send command",
                error = e.message ?: "Unknown error"
            )
        }
    }

    private fun sendFCMNotification(customer: Customer, command: DeviceCommand): FcmSendResult {
        val fcmToken = customer.fcmToken
        if (fcmToken == null || fcmToken.isBlank()) {
            return FcmSendResult(success = false, error = "No FCM token available")
        }
        
        return try {
            // Parse commandData as JSON payload if it exists; if it's a plain string, wrap safely.
            val payloadData: com.fasterxml.jackson.databind.JsonNode = run {
                val data = command.commandData?.trim().orEmpty()
                if (data.isBlank()) {
                    objectMapper.createObjectNode()
                } else {
                    val parsed = try {
                        objectMapper.readTree(data)
                    } catch (_: Exception) {
                        null
                    }

                    when {
                        parsed != null -> parsed
                        else -> objectMapper.createObjectNode().put("data", data)
                    }
                }
            }
            
            // Build FCM payload matching documentation format:
            // { "command_id": "xxx", "command": "LOCK_DEVICE", "payload": {...} }
            val fcmPayload = objectMapper.createObjectNode().apply {
                put("command_id", command.id?.toString() ?: "cmd-${System.currentTimeMillis()}")
                put("command", command.commandType)
                set<com.fasterxml.jackson.databind.JsonNode>("payload", payloadData)
            }
            
            val fcmPayloadString = objectMapper.writeValueAsString(fcmPayload)
            val response = fcmService.sendDataOnly(fcmToken, mapOf("payload" to fcmPayloadString))
            if (response.success) {
                logger.info("FCM notification sent successfully for command ${command.id}")
                FcmSendResult(success = true)
            } else {
                val errorMsg = response.error ?: "FCM send failed"
                logger.error("FCM send failed for command ${command.id}: $errorMsg")
                FcmSendResult(success = false, error = "FCM failed: $errorMsg")
            }
        } catch (e: Exception) {
            // Log error but don't crash the request
            logger.error("Exception sending FCM notification for command ${command.id}: ${e.message}", e)
            FcmSendResult(success = false, error = "FCM failed: ${e.message ?: "FCM send exception"}")
        }
    }
    
    /**
     * Process queued commands and retry failed/pending commands only.
     * Does NOT retry commands with status 'sent' or 'executing' (FCM already delivered; device will report executed/failed).
     * Runs every hour to check for commands that need retry.
     */
    @Transactional
    fun processCommandQueue() {
        val now = Instant.now()
        
        // First, expire commands that are past their expiry time
        expireCommands(now)
        
        // Process queued commands (device was offline; retry when device may be online)
        processQueuedCommands(now)
        
        // Retry only pending and failed commands (never retry 'sent' or 'executing')
        retryUnverifiedCommands(now)
        
        // Re-send FCM for 'sent' commands not yet verified (10, 15, 20... min schedule for 24h)
        resendUnverifiedSentCommands(now)
    }
    
    /**
     * Expire commands that are older than 24 hours
     * Commands expire 24 hours after they were queued
     */
    @Transactional
    private fun expireCommands(now: Instant) {
        val expiredCommands = deviceCommandRepository.findAll().filter { command ->
            command.expiryAt != null && 
            command.expiryAt!!.isBefore(now) &&
            command.status in listOf(CommandStatus.pending, CommandStatus.queued, CommandStatus.sent, CommandStatus.executing)
        }
        
        expiredCommands.forEach { command ->
            command.status = CommandStatus.expired
            command.errorMessage = "Command expired after 24 hours"
            command.updatedAt = now
            deviceCommandRepository.save(command)
            
            // Log activity
            val activity = Activity().apply {
                this.deviceId = command.deviceId
                this.activityType = "command_expired"
                this.activityDescription = "Command ${command.commandType} expired after 24 hours"
                this.createdAt = now
            }
            activityRepository.save(activity)
        }
    }
    
    /**
     * Process queued commands - check if device is now online
     * Retries every hour until device comes online or command expires
     */
    @Transactional
    private fun processQueuedCommands(now: Instant) {
        val queuedCommands = deviceCommandRepository.findAll().filter { command ->
            command.status == CommandStatus.queued &&
            command.nextRetryAt != null &&
            command.nextRetryAt!!.isBefore(now) &&
            (command.expiryAt == null || command.expiryAt!!.isAfter(now))
        }
        
        queuedCommands.forEach { command ->
            val device = deviceStatusRepository.findByDeviceId(command.deviceId).orElse(null)
            
            if (device != null && device.status == DeviceStatusEnum.online) {
                // Device is now online, execute command immediately
                val customer = customerRepository.findByCustomerId(device.customerId).orElse(null)
                val fcmToken = customer?.fcmToken
                
                if (customer != null && fcmToken != null && fcmToken.isNotBlank()) {
                    command.status = CommandStatus.pending
                    command.nextRetryAt = null
                    command.updatedAt = now
                    deviceCommandRepository.save(command)
                    
                    // Send FCM notification and check result
                    val sendResult = sendFCMNotification(customer, command)
                    
                    if (sendResult.success) {
                        // Only set status to 'sent' if FCM actually succeeded
                        command.status = CommandStatus.sent
                        command.errorMessage = null
                        logger.info("FCM notification sent successfully for queued command ${command.id}")
                    } else {
                        // FCM failed - set status to 'failed' with error message
                        command.status = CommandStatus.failed
                        command.errorMessage = sendResult.error ?: "FCM send failed"
                        logger.error("FCM send failed for queued command ${command.id}: ${sendResult.error}")
                    }
                    command.updatedAt = Instant.now()
                    deviceCommandRepository.save(command)
                    
                    // Log activity with command name
                    val activity = Activity().apply {
                        this.deviceId = command.deviceId
                        this.customerId = device.customerId
                        this.activityType = "command_sent"
                        this.activityDescription = "Command '${command.commandType}' sent to device when it came online"
                        this.createdAt = now
                    }
                    activityRepository.save(activity)
                }
            } else {
                // Device still offline, schedule next retry (every hour)
                command.retryCount++
                command.lastRetryAt = now
                command.nextRetryAt = now.plus(1, ChronoUnit.HOURS)
                
                // Check if max retries reached (24 hours = 24 retries at 1 hour intervals)
                if (command.retryCount >= 24) {
                    command.status = CommandStatus.expired
                    command.errorMessage = "Max retries reached (24 hours)"
                    command.nextRetryAt = null
                    
                    // Log activity
                    val activity = Activity().apply {
                        this.deviceId = command.deviceId
                        this.activityType = "command_expired"
                        this.activityDescription = "Command ${command.commandType} expired after 24 retry attempts"
                        this.createdAt = now
                    }
                    activityRepository.save(activity)
                }
                
                command.updatedAt = now
                deviceCommandRepository.save(command)
            }
        }
    }
    
    /**
     * Retry only commands with status pending or failed.
     * Does not retry 'sent' or 'executing' (FCM was already delivered; wait for device to report executed/failed).
     */
    @Transactional
    fun retryUnverifiedCommands(now: Instant) {
        val retryTime = now.minus(10, ChronoUnit.MINUTES)
        
        val commandsToRetry = deviceCommandRepository.findCommandsForRetry(now, retryTime)
        
        commandsToRetry.forEach { command ->
            // Skip if already expired
            if (command.expiryAt != null && command.expiryAt!!.isBefore(now)) {
                return@forEach
            }
            
            val device = deviceStatusRepository.findByDeviceId(command.deviceId).orElse(null)
            val customer = device?.let { deviceStatus ->
                customerRepository.findByCustomerId(deviceStatus.customerId).orElse(null)
            }
            val fcmToken = customer?.fcmToken
            
            if (customer != null && fcmToken != null && fcmToken.isNotBlank()) {
                // Update retry count and timestamp
                command.retryCount++
                command.lastRetryAt = now
                command.updatedAt = now
                deviceCommandRepository.save(command)
                
                // Send FCM notification and check result
                val sendResult = sendFCMNotification(customer, command)
                
                if (sendResult.success) {
                    // Only set status to 'sent' if FCM actually succeeded
                    if (command.status == CommandStatus.pending) {
                        command.status = CommandStatus.sent
                    }
                    command.errorMessage = null
                    logger.info("FCM notification sent successfully for retry command ${command.id}")
                } else {
                    // FCM failed - set status to 'failed' with error message
                    command.status = CommandStatus.failed
                    command.errorMessage = sendResult.error ?: "FCM send failed"
                    logger.error("FCM send failed for retry command ${command.id}: ${sendResult.error}")
                }
                command.updatedAt = now
                deviceCommandRepository.save(command)
            } else {
                // Mark as failed if no FCM token
                command.status = CommandStatus.failed
                command.errorMessage = "No FCM token available"
                command.updatedAt = now
                deviceCommandRepository.save(command)
            }
        }
    }
    
    /**
     * Re-send FCM for commands with status 'sent' that have not been verified.
     * Schedule: 10 min, then 15, 20, 25... (add 5 min each) for 24 hours from creation.
     */
    @Transactional
    private fun resendUnverifiedSentCommands(now: Instant) {
        val tenMinAgo = now.minus(10, ChronoUnit.MINUTES)
        val commandsToResend = deviceCommandRepository.findSentCommandsForResend(now, tenMinAgo)
        
        commandsToResend.forEach { command ->
            val device = deviceStatusRepository.findByDeviceId(command.deviceId).orElse(null) ?: return@forEach
            val customer = customerRepository.findByCustomerId(device.customerId).orElse(null) ?: return@forEach
            val fcmToken = customer.fcmToken
            
            if (fcmToken.isNullOrBlank()) return@forEach
            
            command.retryCount++
            command.lastRetryAt = now
            val delayMinutes = 10 + command.retryCount * 5
            command.nextRetryAt = now.plus(delayMinutes.toLong(), ChronoUnit.MINUTES)
            command.updatedAt = now
            deviceCommandRepository.save(command)
            
            val sendResult = sendFCMNotification(customer, command)
            if (sendResult.success) {
                logger.info("Re-sent FCM for unverified command ${command.id} (retry #${command.retryCount})")
            } else {
                logger.warn("Re-send FCM failed for command ${command.id}: ${sendResult.error}")
            }
        }
    }
    
    /**
     * Legacy method for backward compatibility
     * Now delegates to processCommandQueue
     */
    @Transactional
    fun retryFailedCommands() {
        processCommandQueue()
    }
    
    /**
     * Process command immediately after creation
     * Checks device online status and sends FCM if device is online
     * Otherwise queues command for retry
     * This runs immediately in background when API receives command
     */
    fun processCommandImmediately(command: DeviceCommand, customer: Customer, device: com.da_emi_locker.backend.entity.DeviceStatus) {
        try {
            logger.info("🚀 Processing command ${command.id} IMMEDIATELY in background for device ${device.deviceId}")
            
            // Reload command from database to ensure we have latest version
            val commandId = command.id ?: return
            val currentCommand = deviceCommandRepository.findById(commandId).orElse(null)
                ?: run {
                    logger.error("Command ${commandId} not found in database")
                    return
                }
            
            // Check if customer has FCM token
            val fcmToken = customer.fcmToken?.trim().orEmpty()
            val hasFcmToken = fcmToken.isNotBlank()
            
            logger.info("Customer ${customer.customerId} has FCM token: $hasFcmToken")
            
            if (hasFcmToken) {
                // Has FCM token - send immediately (let FCM handle offline queueing)
                logger.info("✅ Customer has FCM token, sending FCM notification IMMEDIATELY")
                val sendResult = sendFCMNotification(customer, currentCommand)
                
                if (sendResult.success) {
                    // FCM succeeded - update status to 'sent'
                    currentCommand.status = CommandStatus.sent
                    currentCommand.errorMessage = null
                    currentCommand.updatedAt = Instant.now()
                    deviceCommandRepository.save(currentCommand)
                    logger.info("✅ FCM notification sent successfully for command ${commandId}. Status updated to 'sent'")
                } else {
                    // FCM failed - set status to 'failed' with error message
                    currentCommand.status = CommandStatus.failed
                    currentCommand.errorMessage = sendResult.error ?: "FCM send failed"
                    currentCommand.updatedAt = Instant.now()
                    deviceCommandRepository.save(currentCommand)
                    logger.error("❌ FCM send failed for command ${commandId}: ${sendResult.error}. Status updated to 'failed'")
                }
            } else {
                // No FCM token - queue for retry and record reason
                val reason = "No FCM token available"
                logger.info("⏳ Queueing command ${commandId} for retry: $reason")
                currentCommand.status = CommandStatus.queued
                currentCommand.errorMessage = reason
                currentCommand.nextRetryAt = Instant.now().plus(1, ChronoUnit.HOURS)
                currentCommand.updatedAt = Instant.now()
                deviceCommandRepository.save(currentCommand)
                logger.info("Command ${commandId} status updated to 'queued'")
            }
        } catch (e: Exception) {
            logger.error("❌ Error processing command ${command.id} immediately", e)
            // Mark as queued for retry if processing fails
            try {
                val commandId = command.id
                if (commandId != null) {
                    val currentCommand = deviceCommandRepository.findById(commandId).orElse(null)
                    if (currentCommand != null) {
                        currentCommand.status = CommandStatus.queued
                        currentCommand.errorMessage = "Error processing command: ${e.message}"
                        currentCommand.nextRetryAt = Instant.now().plus(1, ChronoUnit.HOURS)
                        currentCommand.updatedAt = Instant.now()
                        deviceCommandRepository.save(currentCommand)
                        logger.error("Command ${commandId} status updated to 'queued' after error")
                    }
                }
            } catch (saveError: Exception) {
                logger.error("Failed to save command status after processing error", saveError)
            }
        }
    }
    
    /**
     * Process pending commands for a specific device
     * Called when device comes online (heartbeat)
     * Executes all queued/pending commands immediately
     */
    @Transactional
    fun processPendingCommandsForDevice(deviceId: String) {
        val now = Instant.now()
        val device = deviceStatusRepository.findByDeviceId(deviceId).orElse(null) ?: return
        
        // Only process if device is online
        if (device.status != DeviceStatusEnum.online) {
            return
        }
        
        val customer = customerRepository.findByCustomerId(device.customerId).orElse(null) ?: return
        
        // Get all queued/pending commands for this device
        val pendingCommands = deviceCommandRepository.findByDeviceId(deviceId)
            .filter { command ->
                command.status in listOf(CommandStatus.queued, CommandStatus.pending) &&
                (command.expiryAt == null || command.expiryAt!!.isAfter(now))
            }
        
        if (pendingCommands.isEmpty()) {
            return
        }
        
        val fcmToken = customer.fcmToken
        if (fcmToken == null || fcmToken.isBlank()) {
            return
        }
        
        pendingCommands.forEach { command ->
            // Execute command immediately since device is online
            command.status = CommandStatus.pending
            command.nextRetryAt = null
            command.updatedAt = now
            deviceCommandRepository.save(command)
            
            // Send FCM notification and check result
            val sendResult = sendFCMNotification(customer, command)
            
            if (sendResult.success) {
                // Only set status to 'sent' if FCM actually succeeded
                command.status = CommandStatus.sent
                command.errorMessage = null
                logger.info("FCM notification sent successfully for pending command ${command.id}")
            } else {
                // FCM failed - set status to 'failed' with error message
                command.status = CommandStatus.failed
                command.errorMessage = sendResult.error ?: "FCM send failed"
                logger.error("FCM send failed for pending command ${command.id}: ${sendResult.error}")
            }
            command.updatedAt = Instant.now()
            deviceCommandRepository.save(command)
            
            // Log activity with command name
            val activity = Activity().apply {
                this.deviceId = deviceId
                this.customerId = device.customerId
                this.activityType = "command_sent"
                this.activityDescription = "Command '${command.commandType}' sent to device when it came online"
                this.createdAt = now
            }
            activityRepository.save(activity)
        }
    }
}

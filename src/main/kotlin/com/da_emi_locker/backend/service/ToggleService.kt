package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.Activity
import com.da_emi_locker.backend.entity.DeviceCommand
import com.da_emi_locker.backend.entity.CommandStatus
import com.da_emi_locker.backend.entity.ToggleState
import com.da_emi_locker.backend.repository.ActivityRepository
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DeviceCommandRepository
import com.da_emi_locker.backend.repository.DeviceStatusRepository
import com.da_emi_locker.backend.repository.ToggleStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class ToggleService(
    private val toggleStateRepository: ToggleStateRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val customerRepository: CustomerRepository,
    private val deviceCommandRepository: DeviceCommandRepository,
    private val activityRepository: ActivityRepository,
    private val fcmService: FCMService,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper
) {
    
    data class ToggleRequest(
        val customerId: String,
        val toggleType: String,
        val enabled: Boolean
    )
    
    data class ToggleResponse(
        val success: Boolean,
        val message: String,
        val toggleState: ToggleStateData? = null
    )
    
    data class ToggleStateData(
        val toggleType: String,
        val state: Boolean,
        val lastChangedAt: String?
    )
    
    data class ToggleStatesResponse(
        val success: Boolean,
        val message: String,
        val toggles: Map<String, Boolean>? = null
    )
    
    // Mapping from toggle_type to (Enabled_Command, Disabled_Command)
    private val toggleCommandMap = mapOf(
        "lock_task" to Pair("LOCK_TASK", "UNLOCK_TASK"),
        "device_lock" to Pair("LOCK_DEVICE", "UNLOCK_TASK"),
        "block_usb" to Pair("USB_BLOCK", "USB_UNBLOCK"),
        "block_camera" to Pair("CAMERA_BLOCK", "CAMERA_UNBLOCK"),
        "block_factory_reset" to Pair("FACTORY_RESET_BLOCK", "FACTORY_RESET_UNBLOCK"),
        "block_install" to Pair("INSTALL_BLOCK", "INSTALL_UNBLOCK"),
        "block_unknown_sources" to Pair("INSTALL_FROM_UNKNOWN_SOURCES_BLOCK", "INSTALL_FROM_UNKNOWN_SOURCES_UNBLOCK"),
        "block_outgoing_calls" to Pair("OUTGOING_CALLS_BLOCK", "OUTGOING_CALLS_UNBLOCK"),
        "hide_apps" to Pair("HIDE_APPS", "UNHIDE_APPS"),
        "restrict_wallpaper" to Pair("RESTRICT_WALLPAPER", "RESTRICT_WALLPAPER_UNBLOCK"),
        "location_enabled" to Pair("LOCATION_ENABLE", "LOCATION_DISABLE"),
        "block_download" to Pair("DOWNLOAD_BLOCK", "DOWNLOAD_UNBLOCK")
    )
    
    private val validToggleTypes = toggleCommandMap.keys
    
    @Transactional
    fun setToggle(dealerId: String, request: ToggleRequest): ToggleResponse {
        // Verify customer belongs to dealer
        val customer = customerRepository.findByCustomerId(request.customerId)
            .orElse(null) ?: return ToggleResponse(
                success = false,
                message = "Customer not found"
            )
        
        if (customer.dealerId != dealerId) {
            return ToggleResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        // Validate toggle type
        if (!validToggleTypes.contains(request.toggleType)) {
            return ToggleResponse(
                success = false,
                message = "Invalid toggle type: ${request.toggleType}"
            )
        }
        
        // Get device for customer
        val devices = deviceStatusRepository.findByCustomerId(request.customerId)
        val device = devices.firstOrNull() ?: return ToggleResponse(
            success = false,
            message = "No device found for customer"
        )
        
        // Update DB State
        val toggleState = toggleStateRepository.findByDeviceIdAndToggleType(device.deviceId, request.toggleType)
            .orElseGet {
                ToggleState().apply {
                    this.deviceId = device.deviceId
                    this.toggleType = request.toggleType
                    this.createdAt = Instant.now()
                    this.updatedAt = Instant.now()
                }
            }
        
        toggleState.state = request.enabled
        toggleState.lastChangedAt = Instant.now()
        toggleState.changedBy = dealerId
        toggleState.updatedAt = Instant.now()
        
        val savedToggle = toggleStateRepository.save(toggleState)
        
        // Determine Command Type
        val (enabledCmd, disabledCmd) = toggleCommandMap[request.toggleType]!!
        val commandType = if (request.enabled) enabledCmd else disabledCmd
        
        // Create command for immediate execution if device is online
        // Note: We always create a command entry for history tracking
        val command = DeviceCommand().apply {
            this.deviceId = device.deviceId
            this.commandType = commandType
            // For simple toggles, payload is often empty, but we can verify spec.
            // Most BLOCK/UNBLOCK commmands have no payload.
            this.commandData = "{}" 
            this.status = CommandStatus.pending
            this.expiryAt = Instant.now().plus(24, ChronoUnit.HOURS)
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }
        
        // Special payload handling if needed (e.g. HIDE_APPS needs package list)
        // For now, assuming basic toggles.
        
        val deviceStatus = deviceStatusRepository.findByDeviceId(device.deviceId).orElse(null)
        val isOnline = deviceStatus?.status?.name == "online"
        
        if (isOnline) {
             // If online, we try to send immediately
             command.status = CommandStatus.sent
        } else {
             command.status = CommandStatus.queued
        }
        
        val savedCommand = deviceCommandRepository.save(command)
            
        if (isOnline) {
            sendFCMNotification(customer, savedCommand)
        }
        
        // Log activity with command name
        val activity = Activity().apply {
            this.customerId = request.customerId
            this.deviceId = device.deviceId
            this.activityType = "command_created"
            this.activityDescription = "Command '${commandType}' created (toggle ${request.toggleType} ${if (request.enabled) "enabled" else "disabled"})"
            this.createdAt = Instant.now()
        }
        activityRepository.save(activity)
        
        return ToggleResponse(
            success = true,
            message = "Toggle updated successfully",
            toggleState = ToggleStateData(
                toggleType = savedToggle.toggleType,
                state = savedToggle.state,
                lastChangedAt = savedToggle.lastChangedAt?.toString()
            )
        )
    }
    
    /**
     * Report device unlocked via offline unlock code (called by configure app).
     * Sets device_lock to false so dealer/admin view stays in sync. No FCM sent.
     */
    @Transactional
    fun reportUnlockedByDevice(customerId: String, imei: String?): ToggleResponse {
        val customer = customerRepository.findByCustomerId(customerId.trim())
            .orElse(null) ?: return ToggleResponse(success = false, message = "Customer not found")
        if (imei != null && imei.isNotBlank() && customer.imei1 != imei.trim()) {
            return ToggleResponse(success = false, message = "IMEI mismatch")
        }
        val devices = deviceStatusRepository.findByCustomerId(customerId)
        val device = devices.firstOrNull() ?: return ToggleResponse(
            success = false,
            message = "No device found for customer"
        )
        val toggleState = toggleStateRepository.findByDeviceIdAndToggleType(device.deviceId, "device_lock")
            .orElseGet {
                ToggleState().apply {
                    this.deviceId = device.deviceId
                    this.toggleType = "device_lock"
                    this.state = false
                    this.createdAt = Instant.now()
                    this.updatedAt = Instant.now()
                }
            }
        toggleState.state = false
        toggleState.lastChangedAt = Instant.now()
        toggleState.changedBy = "device_offline_unlock"
        toggleState.updatedAt = Instant.now()
        toggleStateRepository.save(toggleState)
        val activity = Activity().apply {
            this.customerId = customerId
            this.deviceId = device.deviceId
            this.activityType = "device_unlocked_offline"
            this.activityDescription = "Device unlocked via offline unlock code"
            this.createdAt = Instant.now()
        }
        activityRepository.save(activity)
        return ToggleResponse(
            success = true,
            message = "Device reported as unlocked",
            toggleState = ToggleStateData("device_lock", false, toggleState.lastChangedAt?.toString())
        )
    }

    fun getToggles(customerId: String, dealerId: String): ToggleStatesResponse {
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return ToggleStatesResponse(
                success = false,
                message = "Customer not found"
            )
        
        if (customer.dealerId != dealerId) {
            return ToggleStatesResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        val devices = deviceStatusRepository.findByCustomerId(customerId)
        val device = devices.firstOrNull() ?: return ToggleStatesResponse(
            success = false,
            message = "No device found for customer"
        )
        
        val toggleStates = toggleStateRepository.findByDeviceId(device.deviceId)
        val toggleMap = toggleStates.associate { it.toggleType to it.state }
        
        val allToggles = validToggleTypes.associateWith { toggleType ->
            toggleMap[toggleType] ?: false
        }
        
        return ToggleStatesResponse(
            success = true,
            message = "Toggle states retrieved successfully",
            toggles = allToggles
        )
    }
    
    private fun sendFCMNotification(customer: com.da_emi_locker.backend.entity.Customer, command: DeviceCommand) {
        val fcmToken = customer.fcmToken
        if (fcmToken == null || fcmToken.isBlank()) {
            return
        }
        
        // Construct the inner payload JSON object
        // The spec requires:
        // {
        //   "command_id": "...",
        //   "command": "COMMAND_TYPE",
        //   "payload": { ... }
        // }
        // And this whole object is stringified into the 'payload' data field.
        
        val innerPayloadMap = mapOf(
            "command_id" to (command.id?.toString() ?: "cmd-${System.currentTimeMillis()}"),
            "command" to command.commandType,
            "payload" to (if (command.commandData != null && command.commandData != "{}") 
                             objectMapper.readTree(command.commandData) 
                          else emptyMap<String, Any>())
        )
        
        val innerPayloadString = objectMapper.writeValueAsString(innerPayloadMap)
        fcmService.sendDataOnly(fcmToken, mapOf("payload" to innerPayloadString))
    }
}

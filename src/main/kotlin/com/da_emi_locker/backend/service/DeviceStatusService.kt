package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.Activity
import com.da_emi_locker.backend.entity.DeviceCommand
import com.da_emi_locker.backend.entity.DeviceStatusEnum
import com.da_emi_locker.backend.entity.Customer
import com.da_emi_locker.backend.repository.ActivityRepository
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DeviceCommandRepository
import com.da_emi_locker.backend.repository.DeviceStatusRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class DeviceStatusService(
    private val deviceStatusRepository: DeviceStatusRepository,
    private val customerRepository: CustomerRepository,
    private val deviceCommandRepository: DeviceCommandRepository,
    private val activityRepository: ActivityRepository,
    private val deviceCommandService: DeviceCommandService,
    private val simDetailsService: SimDetailsService
) {
    
    data class DeviceStatusResponse(
        val success: Boolean,
        val message: String,
        val deviceStatus: DeviceStatusData? = null
    )
    
    data class DeviceStatusData(
        val deviceId: String,
        val customerId: String,
        val deviceName: String?,
        val deviceType: String?,
        val status: String,
        val isLocked: Boolean,
        val batteryLevel: Int?,
        val signalStrength: Int?,
        val firmwareVersion: String?,
        val lastSeen: String?,
        val latitude: java.math.BigDecimal? = null,
        val longitude: java.math.BigDecimal? = null
    )
    
    data class UpdateDeviceStatusRequest(
        val deviceId: String? = null,
        val customerId: String? = null,
        val status: String? = null,
        val batteryLevel: Int? = null,
        val signalStrength: Int? = null,
        val location: LocationData? = null,
        val simData: String? = null,
        val appVersion: String? = null,
        val osVersion: String? = null
    )
    
    data class LocationData(
        val latitude: Double,
        val longitude: Double
    )
    
    data class UpdateDeviceStatusResponse(
        val success: Boolean,
        val message: String,
        val pendingCommands: List<CommandData>? = null
    )
    
    data class CommandData(
        val commandId: Long,
        val commandType: String,
        val commandData: String?
    )
    
    fun getDeviceStatusByCustomerId(customerId: String, dealerId: String): DeviceStatusResponse {
        // Verify customer belongs to dealer
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return DeviceStatusResponse(
                success = false,
                message = "Customer not found"
            )
        
        if (customer.dealerId != dealerId) {
            return DeviceStatusResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        // Get device status
        val devices = deviceStatusRepository.findByCustomerId(customerId)
        val device = devices.firstOrNull() ?: return DeviceStatusResponse(
            success = false,
            message = "No device found for customer"
        )
        
        // Check if device is locked (from toggle_states or device_commands)
        val isLocked = checkDeviceLocked(device.deviceId)
        
        return DeviceStatusResponse(
            success = true,
            message = "Device status retrieved successfully",
            deviceStatus = DeviceStatusData(
                deviceId = device.deviceId,
                customerId = device.customerId,
                deviceName = device.deviceName,
                deviceType = device.deviceType,
                status = device.status.name,
                isLocked = isLocked,
                batteryLevel = device.batteryLevel,
                signalStrength = device.signalStrength,
                firmwareVersion = device.firmwareVersion,
                lastSeen = device.lastSeen?.toString(),
                latitude = device.latitude,
                longitude = device.longitude
            )
        )
    }
    
    @Transactional
    fun updateDeviceStatus(request: UpdateDeviceStatusRequest): UpdateDeviceStatusResponse {
        val device = when {
            request.customerId != null && request.customerId.isNotBlank() -> {
                deviceStatusRepository.findByCustomerId(request.customerId).firstOrNull()
            }
            request.deviceId != null && request.deviceId.isNotBlank() -> {
                deviceStatusRepository.findByDeviceId(request.deviceId).orElse(null)
            }
            else -> null
        } ?: return UpdateDeviceStatusResponse(
            success = false,
            message = "Device not found (provide customerId or deviceId)"
        )
        
        // Check if device is coming online
        val wasOffline = device.status != DeviceStatusEnum.online
        
        // Update device status.
        // If the status field is omitted, treat any heartbeat as the device being online
        // so that dealer UI does not keep showing "Offline" while lastSeen keeps updating.
        if (request.status.isNullOrBlank()) {
            device.status = DeviceStatusEnum.online
        } else {
            val rawStatus = request.status.trim()
            try {
                device.status = DeviceStatusEnum.valueOf(rawStatus.lowercase())
            } catch (e: IllegalArgumentException) {
                return UpdateDeviceStatusResponse(
                    success = false,
                    message = "Invalid status: $rawStatus"
                )
            }
        }
        
        request.batteryLevel?.let { device.batteryLevel = it }
        request.signalStrength?.let { device.signalStrength = it }
        request.appVersion?.let { device.firmwareVersion = it }
        
        // Update location if provided
        request.location?.let { location ->
            device.latitude = java.math.BigDecimal.valueOf(location.latitude)
            device.longitude = java.math.BigDecimal.valueOf(location.longitude)
        }
        
        // Update last seen
        device.lastSeen = Instant.now()
        device.updatedAt = Instant.now()
        deviceStatusRepository.save(device)
        
        // If SIM details sent with location ping, save them
        request.simData?.takeIf { it.isNotBlank() }?.let { simData ->
            simDetailsService.saveFromDevice(
                SimDetailsService.SaveSimDetailsRequest(
                    customerId = device.customerId,
                    deviceId = device.deviceId,
                    simData = simData
                )
            )
        }
        
        // If device just came online, process pending commands
        val isNowOnline = device.status == DeviceStatusEnum.online
        if (wasOffline && isNowOnline) {
            deviceCommandService.processPendingCommandsForDevice(device.deviceId)
        }
        
        // Get pending commands
        val pendingCommands = deviceCommandRepository.findPendingCommandsByDeviceId(device.deviceId)
            .map { command ->
                CommandData(
                    commandId = command.id ?: 0,
                    commandType = command.commandType,
                    commandData = command.commandData
                )
            }
        
        return UpdateDeviceStatusResponse(
            success = true,
            message = "Device status updated successfully",
            pendingCommands = pendingCommands
        )
    }
    
    private fun checkDeviceLocked(deviceId: String): Boolean {
        // Check for lock-related commands in an active state.
        // We consider both LOCK_DEVICE and LOCK_TASK commands as indicating a locked device.
        val activeStatuses = listOf(
            com.da_emi_locker.backend.entity.CommandStatus.pending,
            com.da_emi_locker.backend.entity.CommandStatus.sent,
            com.da_emi_locker.backend.entity.CommandStatus.executing,
            com.da_emi_locker.backend.entity.CommandStatus.executed
        )
        val lockCommandTypes = setOf(
            DeviceCommandService.CommandAction.LOCK_DEVICE.value,
            DeviceCommandService.CommandAction.LOCK_TASK.value
        )
        val lockCommands = deviceCommandRepository.findByDeviceId(deviceId)
            .filter { command ->
                command.commandType in lockCommandTypes &&
                    command.status in activeStatuses
            }
        return lockCommands.isNotEmpty()
    }
}

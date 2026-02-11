package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.CustomerService
import com.da_emi_locker.backend.service.DealerService
import com.da_emi_locker.backend.service.DeviceCommandService
import com.da_emi_locker.backend.service.DeviceStatusService
import com.da_emi_locker.backend.service.SimDetailsService
import com.da_emi_locker.backend.service.ToggleService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Controller for customer/configure app endpoints
 * These endpoints are called by the configure app (device owner) and customer app
 */
@RestController
@RequestMapping("/api/customer/device")
class CustomerDeviceController(
    private val deviceStatusService: DeviceStatusService,
    private val deviceCommandService: DeviceCommandService,
    private val customerService: CustomerService,
    private val dealerService: DealerService,
    private val simDetailsService: SimDetailsService,
    private val toggleService: ToggleService
) {
    
    data class ActivateDeviceRequestDto(
        val deviceId: String? = null,
        @field:NotBlank(message = "IMEI is required")
        val imei: String = "",
        @field:NotBlank(message = "FCM token is required")
        val fcmToken: String = "",
        // Phone details collected on activation
        val manufacturer: String? = null,
        val model: String? = null,
        val brand: String? = null,
        val androidVersion: String? = null,
        val sdkVersion: String? = null,
        val serialNumber: String? = null
    )
    
    data class UpdateDeviceStatusRequestDto(
        val deviceId: String? = null,
        val customerId: String? = null,
        val status: String? = null,
        val batteryLevel: Int? = null,
        val signalStrength: Int? = null,
        val location: LocationDto? = null,
        val simData: String? = null,
        val appVersion: String? = null,
        val osVersion: String? = null
    )
    
    data class FcmTokenRequestDto(
        @field:NotBlank(message = "Customer ID is required")
        val customerId: String = "",
        @field:NotBlank(message = "FCM token is required")
        val fcmToken: String = "",
        val imei: String? = null
    )
    
    data class LocationDto(
        val latitude: Double,
        val longitude: Double
    )
    
    data class VerifyCommandRequestDto(
        val commandId: Long,
        val success: Boolean,
        val errorMessage: String? = null
    )
    
    data class SimDetailsRequestDto(
        @field:NotBlank(message = "Customer ID is required")
        val customerId: String = "",
        val deviceId: String? = null,
        val simData: String? = null
    )

    data class ReportUnlockedRequestDto(
        @field:NotBlank(message = "Customer ID is required")
        val customerId: String = "",
        val imei: String? = null
    )

    data class DeregisterDeviceRequestDto(
        @field:NotBlank(message = "Customer ID is required")
        val customerId: String = "",
        val imei: String? = null
    )
    
    @PostMapping("/activate")
    fun activateDevice(
        @Valid @RequestBody request: ActivateDeviceRequestDto
    ): ResponseEntity<CustomerService.ActivateDeviceResponse> {
        val phoneDetails = mutableMapOf<String, String?>()
        request.manufacturer?.let { phoneDetails["manufacturer"] = it }
        request.model?.let { phoneDetails["model"] = it }
        request.brand?.let { phoneDetails["brand"] = it }
        request.androidVersion?.let { phoneDetails["androidVersion"] = it }
        request.sdkVersion?.let { phoneDetails["sdkVersion"] = it }
        request.serialNumber?.let { phoneDetails["serialNumber"] = it }

        val response = customerService.activateDevice(
            deviceId = request.deviceId?.takeIf { it.isNotBlank() },
            imei = request.imei.trim(),
            fcmToken = request.fcmToken.trim(),
            phoneDetails = if (phoneDetails.isNotEmpty()) phoneDetails else null
        )
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    @GetMapping("/by-imei")
    fun getByImei(@RequestParam imei: String): ResponseEntity<CustomerService.ActivateDeviceResponse> {
        val response = customerService.getCustomerByImei(imei.trim())
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(404).body(response)
        }
    }
    
    @PostMapping("/fcm-token")
    fun updateFcmTokenFromDevice(
        @Valid @RequestBody request: FcmTokenRequestDto
    ): ResponseEntity<CustomerService.CustomerResponse> {
        val response = customerService.updateFCMTokenFromDevice(
            customerId = request.customerId.trim(),
            fcmToken = request.fcmToken.trim(),
            imei = request.imei?.takeIf { it.isNotBlank() }
        )
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    @PostMapping("/status")
    fun updateDeviceStatus(
        @RequestBody request: UpdateDeviceStatusRequestDto
    ): ResponseEntity<DeviceStatusService.UpdateDeviceStatusResponse> {
        if (request.deviceId.isNullOrBlank() && request.customerId.isNullOrBlank()) {
            return ResponseEntity.status(400).body(
                DeviceStatusService.UpdateDeviceStatusResponse(
                    success = false,
                    message = "Either deviceId or customerId is required"
                )
            )
        }
        val response = deviceStatusService.updateDeviceStatus(
            DeviceStatusService.UpdateDeviceStatusRequest(
                deviceId = request.deviceId?.takeIf { it.isNotBlank() },
                customerId = request.customerId?.takeIf { it.isNotBlank() },
                status = request.status,
                batteryLevel = request.batteryLevel,
                signalStrength = request.signalStrength,
                location = request.location?.let {
                    DeviceStatusService.LocationData(
                        latitude = it.latitude,
                        longitude = it.longitude
                    )
                },
                simData = request.simData?.takeIf { it.isNotBlank() },
                appVersion = request.appVersion,
                osVersion = request.osVersion
            )
        )
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    @PostMapping("/sim-details")
    fun saveSimDetailsFromDevice(@RequestBody request: SimDetailsRequestDto): ResponseEntity<SimDetailsService.SaveSimDetailsResponse> {
        val response = simDetailsService.saveFromDevice(
            SimDetailsService.SaveSimDetailsRequest(
                customerId = request.customerId.trim(),
                deviceId = request.deviceId?.takeIf { it.isNotBlank() },
                simData = request.simData
            )
        )
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(400).body(response)
    }
    
    @PostMapping("/command/verify")
    fun verifyCommandExecution(
        @Valid @RequestBody request: VerifyCommandRequestDto
    ): ResponseEntity<DeviceCommandService.VerifyCommandResponse> {
        val response = deviceCommandService.verifyCommandExecution(
            DeviceCommandService.VerifyCommandRequest(
                commandId = request.commandId,
                success = request.success,
                errorMessage = request.errorMessage
            )
        )
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }

    /** Configure app: report device unlocked via offline unlock code. Syncs backend so dealer/admin see device as unlocked. */
    @PostMapping("/report-unlocked")
    fun reportUnlocked(
        @RequestBody request: ReportUnlockedRequestDto
    ): ResponseEntity<ToggleService.ToggleResponse> {
        val response = toggleService.reportUnlockedByDevice(
            customerId = request.customerId.trim(),
            imei = request.imei?.takeIf { it.isNotBlank() }
        )
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(400).body(response)
    }

    /** Configure app: deregister device when app is uninstalled (REMOVE_DEVICE_OWNER). Clears IMEI and sets status to uninstalled. */
    @PostMapping("/deregister")
    fun deregisterDevice(
        @RequestBody request: DeregisterDeviceRequestDto
    ): ResponseEntity<CustomerService.CustomerResponse> {
        val response = customerService.deregisterDevice(
            customerId = request.customerId.trim(),
            imei = request.imei?.takeIf { it.isNotBlank() }
        )
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(400).body(response)
    }
}

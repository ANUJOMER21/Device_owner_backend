package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.service.DeviceOwnerConfigService
import com.da_emi_locker.backend.service.DealerService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/qr-code")
class QRCodeController(
    private val dealerService: DealerService,
    private val deviceOwnerConfigService: DeviceOwnerConfigService,
    private val customerRepository: CustomerRepository,
    private val objectMapper: ObjectMapper
) {
    
    data class QRCodeResponse(
        val success: Boolean,
        val message: String,
        val data: QRCodeData? = null
    )
    
    data class QRCodeData(
        val appName: String,
        val appType: String,
        val version: String,
        val timestamp: String,
        val type: String,
        val installUrl: String? = null,
        val customerId: String? = null,
        val imei: String? = null,
        val qrData: String? = null // JSON string for QR code
    )
    
    // ApiResponse wrapper for dealer app compatibility
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )
    
    // QRCodeDto matching dealer app format
    data class QRCodeDto(
        val qrData: String // Universal JSON string with app info
    )
    
    private val deviceAdminComponent = "com.omer.aocdoapp/com.omer.aocdoapp.admin.EmiDeviceAdminReceiver"
    
    /** Build Android Device Owner provisioning JSON for QR code. */
    private fun buildDeviceOwnerProvisioningJson(
        apkUrl: String,
        apkSha256Base64: String,
        dealerId: String,
        customerId: String? = null
    ): String {
        val extras = mutableMapOf<String, String>("dealer_id" to dealerId)
        customerId?.let { extras["customer_id"] = it }
        val provisioning = mapOf(
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME" to deviceAdminComponent,
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION" to apkUrl,
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM" to apkSha256Base64,
            "android.app.extra.PROVISIONING_SKIP_ENCRYPTION" to true,
            "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED" to true,
            "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE" to extras
        )
        return objectMapper.writeValueAsString(provisioning)
    }
    
    /**
     * Dealer dashboard FAB: Device Owner provisioning QR with dealer_id only (no customer).
     */
    @GetMapping
    fun getQRCodeData(
        @RequestAttribute("dealerId") dealerId: String?
    ): ResponseEntity<ApiResponse<QRCodeDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized")
            )
        }
        val dealer = dealerService.getDealerInfo(dealerId)
            ?: return ResponseEntity.status(404).body(
                ApiResponse(success = false, message = "Dealer not found")
            )
        val config = deviceOwnerConfigService.getConfig()
            ?: return ResponseEntity.status(503).body(
                ApiResponse(
                    success = false,
                    message = "Device Owner APK not configured. Admin must upload APK in Settings."
                )
            )
        val qrData = buildDeviceOwnerProvisioningJson(
            apkUrl = config.apkUrl,
            apkSha256Base64 = config.apkSha256Base64,
            dealerId = dealerId,
            customerId = null
        )
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "QR code data generated successfully",
                data = QRCodeDto(qrData = qrData)
            )
        )
    }
    
    /**
     * After adding customer: Device Owner provisioning QR with dealer_id + customer_id.
     * Requires dealer auth; customer must belong to this dealer.
     */
    @GetMapping("/customer/{customerId}")
    fun getCustomerQRCode(
        @PathVariable customerId: String,
        @RequestAttribute("dealerId") dealerId: String?
    ): ResponseEntity<QRCodeResponse> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                QRCodeResponse(success = false, message = "Unauthorized")
            )
        }
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return ResponseEntity.status(404).body(
                QRCodeResponse(success = false, message = "Customer not found")
            )
        if (customer.dealerId != dealerId) {
            return ResponseEntity.status(403).body(
                QRCodeResponse(success = false, message = "Customer does not belong to your dealer account")
            )
        }
        val config = deviceOwnerConfigService.getConfig()
            ?: return ResponseEntity.status(503).body(
                QRCodeResponse(
                    success = false,
                    message = "Device Owner APK not configured. Admin must upload APK in Settings."
                )
            )
        val qrData = buildDeviceOwnerProvisioningJson(
            apkUrl = config.apkUrl,
            apkSha256Base64 = config.apkSha256Base64,
            dealerId = dealerId,
            customerId = customerId
        )
        return ResponseEntity.ok(
            QRCodeResponse(
                success = true,
                message = "Customer QR code data generated successfully",
                data = QRCodeData(
                    appName = "DA EMI Locker - Device Owner Install",
                    appType = "device_owner",
                    version = "1.0.0",
                    timestamp = Instant.now().toString(),
                    type = "device_owner_provisioning",
                    installUrl = null,
                    customerId = customerId,
                    imei = null,
                    qrData = qrData
                )
            )
        )
    }
}

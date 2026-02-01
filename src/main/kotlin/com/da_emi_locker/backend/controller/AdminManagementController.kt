package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DealerRepository
import com.da_emi_locker.backend.repository.DeviceStatusRepository
import com.da_emi_locker.backend.repository.DeviceCommandRepository
import com.da_emi_locker.backend.repository.ToggleStateRepository
import com.da_emi_locker.backend.service.S3StorageService
import com.da_emi_locker.backend.service.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
class AdminManagementController(
    private val adminDealerService: AdminDealerService,
    private val adminDashboardService: AdminDashboardService,
    private val adminNotificationService: AdminNotificationService,
    private val activityService: ActivityService,
    private val supportTicketService: SupportTicketService,
    private val customerService: CustomerService,
    private val aadharService: AadharService,
    private val panService: PANService,
    private val loanService: LoanService,
    private val deviceCommandService: DeviceCommandService,
    private val toggleService: ToggleService,
    private val commandHistoryService: CommandHistoryService,
    private val dealerPaymentService: DealerPaymentService,
    private val customerRepository: CustomerRepository,
    private val dealerRepository: DealerRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val deviceCommandRepository: DeviceCommandRepository,
    private val toggleStateRepository: ToggleStateRepository,
    private val simDetailsService: SimDetailsService,
    private val deviceOwnerConfigService: DeviceOwnerConfigService,
    private val s3StorageService: S3StorageService
) {
    
    // Admin Dashboard
    @GetMapping("/dashboard")
    fun getAdminDashboard(): ResponseEntity<AdminDashboardService.AdminDashboardResponse> {
        val response = adminDashboardService.getAdminDashboard()
        return ResponseEntity.ok(response)
    }
    
    // Admin Dashboard - Pending Actions (open tickets, failed commands, suspended dealers)
    @GetMapping("/dashboard/pending-actions")
    fun getPendingActions(): ResponseEntity<AdminDashboardService.PendingActionsResponse> {
        val response = adminDashboardService.getPendingActions()
        return ResponseEntity.ok(response)
    }
    
    // Admin Dashboard - Reports (analytics: stats + activity by type)
    @GetMapping("/dashboard/reports")
    fun getReports(): ResponseEntity<AdminDashboardService.ReportsResponse> {
        val response = adminDashboardService.getReports()
        return ResponseEntity.ok(response)
    }
    
    // Dealer Dashboard
    @GetMapping("/dealers/{dealerId}/dashboard")
    fun getDealerDashboard(@PathVariable dealerId: String): ResponseEntity<AdminDealerService.DealerDashboardResponse> {
        val response = adminDealerService.getDealerDashboard(dealerId)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(404).body(response)
        }
    }
    
    // Dealer Customers
    @GetMapping("/dealers/{dealerId}/customers")
    fun getDealerCustomers(
        @PathVariable dealerId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<CustomerService.CustomerListResponse> {
        val response = adminDealerService.getDealerCustomers(dealerId, page, pageSize)
        return ResponseEntity.ok(response)
    }

    // Dealer Payments (Admin filters)
    @GetMapping("/dealer-payments")
    fun getDealerPayments(
        @RequestParam(required = false) dealerId: String?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<DealerPaymentService.PaymentListResponse> {
        val response = dealerPaymentService.getDealerPaymentsForAdmin(dealerId, startDate, endDate, page, pageSize)
        return ResponseEntity.ok(response)
    }
    
    // Reverse transaction (delete kit) - creates reversal entry in payment history
    data class ReversePaymentRequest(
        @field:NotBlank(message = "paymentId is required")
        val paymentId: String,
        val reason: String? = null,
        /** Refund amount (money) for this reversal; optional. */
        val refundAmount: java.math.BigDecimal? = null,
        /** Medium of refund (e.g. cash, UPI, bank_transfer); optional. */
        val refundMedium: String? = null
    )
    
    @PostMapping("/dealer-payments/reverse")
    fun reverseDealerPayment(@Valid @RequestBody request: ReversePaymentRequest): ResponseEntity<DealerPaymentService.PaymentResponse> {
        val response = dealerPaymentService.reversePayment(
            request.paymentId,
            request.reason,
            request.refundAmount,
            request.refundMedium
        )
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
        }
    }
    
    // Admin update customer FCM token by customerId
    data class UpdateCustomerFcmTokenRequestDto(
        @field:NotBlank(message = "FCM token is required")
        val fcmToken: String
    )
    
    @PutMapping("/customers/{customerId}/fcm-token")
    fun updateCustomerFcmToken(
        @PathVariable customerId: String,
        @Valid @RequestBody request: UpdateCustomerFcmTokenRequestDto
    ): ResponseEntity<CustomerService.CustomerResponse> {
        val response = customerService.updateFCMToken(customerId, request.fcmToken)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    // Admin update customer FCM token by IMEI
    @PutMapping("/customers/by-imei/{imei}/fcm-token")
    fun updateCustomerFcmTokenByImei(
        @PathVariable imei: String,
        @Valid @RequestBody request: UpdateCustomerFcmTokenRequestDto
    ): ResponseEntity<CustomerService.CustomerResponse> {
        val customer = customerRepository.findByImei1(imei)
            .orElse(null) ?: return ResponseEntity.status(404).body(
                CustomerService.CustomerResponse(
                    success = false,
                    message = "Customer not found with IMEI: $imei"
                )
            )
        
        val response = customerService.updateFCMToken(customer.customerId, request.fcmToken)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    // Admin get customer details
    @GetMapping("/customers/{customerId}")
    fun getCustomerDetails(@PathVariable customerId: String): ResponseEntity<CustomerService.CustomerResponse> {
        // Admin can view any customer - get customer first to find dealerId
        val customerOpt = customerRepository.findByCustomerId(customerId)
        if (customerOpt.isEmpty) {
            return ResponseEntity.status(404).body(
                CustomerService.CustomerResponse(
                    success = false,
                    message = "Customer not found"
                )
            )
        }
        
        val customer = customerOpt.get()
        val response = customerService.getCustomer(customerId, customer.dealerId)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(404).body(response)
        }
    }

    // Admin: Device Owner provisioning QR for a customer (dealer_id + customer_id in extras)
    @GetMapping("/customers/{customerId}/device-owner-qr")
    fun adminGetDeviceOwnerQR(@PathVariable customerId: String): ResponseEntity<Map<String, Any>> {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                mapOf("success" to false, "message" to "Customer not found")
            )
        val config = deviceOwnerConfigService.getConfig()
            ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                mapOf("success" to false, "message" to "Device Owner APK not configured. Upload APK in Settings.")
            )
        val extras = mutableMapOf<String, String>("dealer_id" to customer.dealerId, "customer_id" to customerId)
        val provisioning = mapOf(
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME" to "com.omer.aocdoapp/com.omer.aocdoapp.admin.EmiDeviceAdminReceiver",
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION" to config.apkUrl,
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM" to config.apkSha256Base64,
            "android.app.extra.PROVISIONING_SKIP_ENCRYPTION" to true,
            "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED" to true,
            "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE" to extras
        )
        val qrData = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(provisioning)
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "data" to mapOf(
                "qrData" to qrData,
                "customerId" to customerId,
                "dealerId" to customer.dealerId
            )
        ))
    }

    @GetMapping("/customers/{customerId}/sim-details")
    fun adminGetSimDetails(@PathVariable customerId: String): ResponseEntity<Map<String, Any>> {
        val sim = simDetailsService.getLatestByCustomerId(customerId)
        return if (sim != null) {
            ResponseEntity.ok(mapOf(
                "success" to true,
                "customerId" to sim.customerId,
                "deviceId" to (sim.deviceId ?: ""),
                "simData" to (sim.simData ?: "{}"),
                "createdAt" to (sim.createdAt?.toString() ?: "")
            ))
        } else {
            ResponseEntity.ok(mapOf("success" to true, "message" to "No SIM details yet", "simData" to "{}"))
        }
    }

    // Admin: generate offline unlock code if missing
    @PostMapping("/customers/{customerId}/offline-unlock-code/generate")
    fun adminGenerateOfflineUnlockCode(
        @PathVariable customerId: String
    ): ResponseEntity<CustomerService.CustomerResponse> {
        val response = customerService.generateOfflineUnlockCodeIfMissing(customerId)
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(404).body(response)
    }

    data class UpdateCustomerDeviceIdRequestDto(
        @field:NotBlank(message = "deviceId is required")
        val deviceId: String
    )

    // Admin: update deviceId mapping for a customer (device_status row)
    @PutMapping("/customers/{customerId}/device-id")
    fun adminUpdateCustomerDeviceId(
        @PathVariable customerId: String,
        @Valid @RequestBody request: UpdateCustomerDeviceIdRequestDto
    ): ResponseEntity<Map<String, Any>> {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                mapOf("success" to false, "message" to "Customer not found")
            )

        // Update existing device_status row for this customer, or create one.
        // If multiple rows exist, we'll keep the first and delete the rest to avoid stale deviceIds.
        val existingForCustomerAll = deviceStatusRepository.findByCustomerId(customerId)
        val existingForCustomer = existingForCustomerAll.firstOrNull()
        val oldDeviceId = existingForCustomer?.deviceId

        // If the new deviceId is already used by some other customer, block it
        val existingByDeviceId = deviceStatusRepository.findByDeviceId(request.deviceId).orElse(null)
        if (existingByDeviceId != null && existingByDeviceId.customerId != customerId) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                mapOf("success" to false, "message" to "Device ID already assigned to another customer")
            )
        }

        if (existingForCustomer != null) {
            // keep customerId, update deviceId
            existingForCustomer.deviceId = request.deviceId
            deviceStatusRepository.save(existingForCustomer)

            // Remove any extra device_status rows for this customer to avoid showing stale deviceId
            if (existingForCustomerAll.size > 1) {
                existingForCustomerAll.drop(1).forEach { deviceStatusRepository.delete(it) }
            }
        } else {
            val ds = com.da_emi_locker.backend.entity.DeviceStatus().apply {
                deviceId = request.deviceId
                this.customerId = customer.customerId
                status = com.da_emi_locker.backend.entity.DeviceStatusEnum.offline
                lastSeen = null
            }
            deviceStatusRepository.save(ds)
        }

        // If deviceId changed, update related rows for continuity (best-effort)
        if (oldDeviceId != null && oldDeviceId != request.deviceId) {
            // Update commands and toggles to point to new deviceId
            deviceCommandRepository.findByDeviceId(oldDeviceId).forEach {
                it.deviceId = request.deviceId
                deviceCommandRepository.save(it)
            }
            toggleStateRepository.findByDeviceId(oldDeviceId).forEach {
                it.deviceId = request.deviceId
                toggleStateRepository.save(it)
            }
        }

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Device ID updated successfully",
                "customerId" to customerId,
                "deviceId" to request.deviceId
            )
        )
    }

    // Admin: Customer documents (Aadhar/PAN/Loan) - bypass dealer auth
    @GetMapping("/customers/{customerId}/aadhar")
    fun adminGetAadhar(@PathVariable customerId: String): ResponseEntity<AadharService.AadharResponse> {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                AadharService.AadharResponse(success = false, message = "Customer not found")
            )
        val response = aadharService.getAadharDetails(customerId, customer.dealerId)
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(404).body(response)
    }

    @PostMapping("/customers/{customerId}/aadhar")
    fun adminSaveAadhar(
        @PathVariable customerId: String,
        @Valid @RequestBody request: DocumentController.AadharRequestDto
    ): ResponseEntity<AadharService.AadharResponse> {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                AadharService.AadharResponse(success = false, message = "Customer not found")
            )
        val response = aadharService.saveAadharDetails(
            customer.dealerId,
            customerId,
            AadharService.AadharRequest(
                aadharNumber = request.aadharNumber,
                fullName = request.fullName,
                dateOfBirth = request.dateOfBirth,
                address = request.address,
                frontImageUrl = request.frontImageUrl,
                backImageUrl = request.backImageUrl
            )
        )
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(400).body(response)
    }

    @GetMapping("/customers/{customerId}/pan")
    fun adminGetPAN(@PathVariable customerId: String): ResponseEntity<PANService.PANResponse> {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                PANService.PANResponse(success = false, message = "Customer not found")
            )
        val response = panService.getPANDetails(customerId, customer.dealerId)
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(404).body(response)
    }

    @PostMapping("/customers/{customerId}/pan")
    fun adminSavePAN(
        @PathVariable customerId: String,
        @Valid @RequestBody request: DocumentController.PANRequestDto
    ): ResponseEntity<PANService.PANResponse> {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                PANService.PANResponse(success = false, message = "Customer not found")
            )
        val response = panService.savePANDetails(
            customer.dealerId,
            customerId,
            PANService.PANRequest(
                panNumber = request.panNumber,
                fullName = request.fullName,
                dateOfBirth = request.dateOfBirth,
                fatherName = request.fatherName,
                imageUrl = request.imageUrl
            )
        )
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(400).body(response)
    }

    @GetMapping("/customers/{customerId}/loan")
    fun adminGetLoan(@PathVariable customerId: String): ResponseEntity<LoanService.LoanResponse> {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                LoanService.LoanResponse(success = false, message = "Customer not found")
            )
        val response = loanService.getLoanDetails(customerId, customer.dealerId)
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(404).body(response)
    }

    @PostMapping("/customers/{customerId}/loan")
    fun adminSaveLoan(
        @PathVariable customerId: String,
        @Valid @RequestBody request: DocumentController.LoanRequestDto
    ): ResponseEntity<LoanService.LoanResponse> {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                LoanService.LoanResponse(success = false, message = "Customer not found")
            )
        val response = loanService.saveLoanDetails(
            customer.dealerId,
            customerId,
            LoanService.LoanRequest(
                productPrice = request.productPrice,
                downPayment = request.downPayment,
                loanAmount = request.loanAmount,
                tenureMonths = request.tenureMonths,
                rateOfInterest = request.rateOfInterest,
                monthlyEmi = request.monthlyEmi,
                emiDate = request.emiDate,
                remark = request.remark
            )
        )
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(400).body(response)
    }

    // Admin list customers (for Customers tab)
    @GetMapping("/customers")
    fun getAllCustomers(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<CustomerService.CustomerListResponse> {
        val pageable: Pageable = PageRequest.of(page, pageSize)

        val statusEnum = if (status.isNullOrBlank()) {
            null
        } else {
            try {
                com.da_emi_locker.backend.entity.CustomerStatus.valueOf(status.lowercase())
            } catch (_: Exception) {
                return ResponseEntity.ok(
                    CustomerService.CustomerListResponse(
                        success = false,
                        message = "Invalid status: $status"
                    )
                )
            }
        }

        val customersPage = customerRepository.searchAllForAdmin(statusEnum, search, pageable)
        val customerDataList = customersPage.content.map { customer ->
            CustomerService.CustomerData(
                customerId = customer.customerId,
                dealerId = customer.dealerId,
                name = customer.name,
                email = customer.email,
                phone = customer.phone,
                address = customer.address,
                status = customer.status.name,
                createdAt = customer.createdAt?.toString() ?: "",
                updatedAt = customer.updatedAt?.toString() ?: "",
                fcmToken = customer.fcmToken,
                customerImageUrl = customer.customerImageUrl,
                signatureImageUrl = customer.signatureImageUrl,
                deviceStatus = null,
                toggleStates = null,
                aadharStatus = customer.aadharStatus,
                panStatus = customer.panStatus,
                loanStatus = customer.loanStatus,
                imei1 = customer.imei1.orEmpty(),
                imei2 = customer.imei2,
                offlineUnlockCode = customer.offlineUnlockCode
            )
        }

        return ResponseEntity.ok(
            CustomerService.CustomerListResponse(
                success = true,
                message = "Customers retrieved successfully",
                customers = customerDataList,
                total = customersPage.totalElements,
                page = customersPage.number,
                pageSize = customersPage.size,
                totalPages = customersPage.totalPages
            )
        )
    }
    
    // Admin create customer
    data class AdminCreateCustomerRequestDto(
        @field:NotBlank(message = "Dealer ID is required")
        val dealerId: String,
        
        @field:NotBlank(message = "Name is required")
        val name: String,
        
        val email: String? = null,
        
        @field:NotBlank(message = "Phone is required")
        val phone: String,
        
        val address: String? = null,

        val customerImageUrl: String? = null,
        val signatureImageUrl: String? = null,
        
        @field:NotBlank(message = "IMEI1 is required")
        val imei1: String,
        
        val imei2: String? = null
    )
    
    @PostMapping("/customers")
    fun createCustomer(
        @Valid @RequestBody request: AdminCreateCustomerRequestDto
    ): ResponseEntity<CustomerService.CustomerResponse> {
        val response = customerService.createCustomer(
            request.dealerId,
            CustomerService.CreateCustomerRequest(
                name = request.name,
                email = request.email,
                phone = request.phone,
                address = request.address,
                customerImageUrl = request.customerImageUrl,
                signatureImageUrl = request.signatureImageUrl,
                imei1 = request.imei1,
                imei2 = request.imei2
            )
        )
        
        return if (response.success) {
            ResponseEntity.status(201).body(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    // Admin can control dealer's customers
    @PutMapping("/customers/{customerId}")
    fun updateCustomer(
        @PathVariable customerId: String,
        @Valid @RequestBody request: CustomerService.UpdateCustomerRequest
    ): ResponseEntity<CustomerService.CustomerResponse> {
        // Admin can update any customer - get customer first to find dealerId
        val customerOpt = customerRepository.findByCustomerId(customerId)
        if (customerOpt.isEmpty) {
            return ResponseEntity.status(404).body(
                CustomerService.CustomerResponse(
                    success = false,
                    message = "Customer not found"
                )
            )
        }
        
        val customer = customerOpt.get()
        val response = customerService.updateCustomer(customerId, customer.dealerId, request)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    @DeleteMapping("/customers/{customerId}")
    fun deleteCustomer(@PathVariable customerId: String): ResponseEntity<CustomerService.CustomerResponse> {
        // Admin can delete any customer
        val customerOpt = customerRepository.findByCustomerId(customerId)
        if (customerOpt.isEmpty) {
            return ResponseEntity.status(404).body(
                CustomerService.CustomerResponse(
                    success = false,
                    message = "Customer not found"
                )
            )
        }
        
        val customer = customerOpt.get()
        val response = customerService.deleteCustomer(customerId, customer.dealerId)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    // Admin send command to dealer's customer
    data class AdminDeviceActionRequest(
        @field:NotBlank(message = "Action is required")
        val action: String,
        val reason: String? = null,
        val payload: Map<String, String>? = null
    )
    
    /** Mark customer as uninstalled (no FCM command sent). Admin cannot change uninstalled status back via update. */
    @PostMapping("/customers/{customerId}/mark-uninstalled")
    fun markCustomerUninstalled(@PathVariable customerId: String): ResponseEntity<CustomerService.CustomerResponse> {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return ResponseEntity.status(404).body(
                CustomerService.CustomerResponse(success = false, message = "Customer not found")
            )
        return try {
            val response = customerService.markCustomerUninstalled(customerId, customer.dealerId)
            if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(400).body(response)
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(AdminManagementController::class.java)
                .error("mark-uninstalled failed for customer $customerId", e)
            ResponseEntity.status(500).body(
                CustomerService.CustomerResponse(
                    success = false,
                    message = e.message ?: "Failed to mark customer as uninstalled"
                )
            )
        }
    }

    @PostMapping("/customers/{customerId}/device/action")
    fun sendDeviceCommand(
        @PathVariable customerId: String,
        @Valid @RequestBody request: AdminDeviceActionRequest
    ): ResponseEntity<DeviceCommandService.DeviceActionResponse> {
        return try {
            // Admin can send commands to any customer (no ownership/security checks)
            val response = deviceCommandService.executeDeviceActionAsAdmin(
                DeviceCommandService.DeviceActionRequest(
                    customerId = customerId,
                    action = request.action,
                    reason = request.reason,
                    payload = request.payload
                )
            )
            
            if (response.success) {
                ResponseEntity.ok(response)
            } else {
                ResponseEntity.status(400).body(response)
            }
        } catch (e: Exception) {
            // Log the error for debugging
            org.slf4j.LoggerFactory.getLogger(AdminManagementController::class.java)
                .error("Error sending device command for customer $customerId", e)
            
            // Return a proper error response
            ResponseEntity.status(500).body(
                DeviceCommandService.DeviceActionResponse(
                    success = false,
                    message = "Failed to send device command: ${e.message ?: "Unknown error"}"
                )
            )
        }
    }
    
    // Admin set device toggle (uses customer's dealerId for authorization)
    data class AdminDeviceToggleRequest(
        @field:NotBlank(message = "Toggle type is required")
        val toggleType: String,
        val enabled: Boolean
    )
    
    @PostMapping("/customers/{customerId}/device/toggle")
    fun setDeviceToggle(
        @PathVariable customerId: String,
        @Valid @RequestBody request: AdminDeviceToggleRequest
    ): ResponseEntity<Map<String, Any>> {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                mapOf("success" to false, "message" to "Customer not found")
            )
        val response = toggleService.setToggle(
            customer.dealerId,
            ToggleService.ToggleRequest(
                customerId = customerId,
                toggleType = request.toggleType,
                enabled = request.enabled
            )
        )
        return if (response.success && response.toggleState != null) {
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to (response.message ?: "Toggle updated"),
                "toggleType" to response.toggleState!!.toggleType,
                "enabled" to response.toggleState!!.state
            ))
        } else {
            ResponseEntity.status(400).body(
                mapOf("success" to false, "message" to (response.message ?: "Toggle update failed"))
            )
        }
    }
    
    // Notifications
    data class SendNotificationRequestDto(
        val dealerId: String? = null,
        @field:NotBlank(message = "Title is required")
        val title: String,
        @field:NotBlank(message = "Body is required")
        val body: String,
        val data: Map<String, String> = emptyMap()
    )
    
    @PostMapping("/notifications/send")
    fun sendNotification(
        @Valid @RequestBody request: SendNotificationRequestDto
    ): ResponseEntity<AdminNotificationService.NotificationResponse> {
        val response = adminNotificationService.sendNotification(
            AdminNotificationService.SendNotificationRequest(
                dealerId = request.dealerId,
                title = request.title,
                body = request.body,
                data = request.data
            )
        )
        return ResponseEntity.ok(response)
    }
    
    // Dealer Activities
    @GetMapping("/dealers/{dealerId}/activities")
    fun getDealerActivities(
        @PathVariable dealerId: String,
        @RequestParam(required = false) activityType: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<ActivityService.ActivityListResponse> {
        val response = activityService.getDealerActivities(dealerId, activityType, page, pageSize)
        return ResponseEntity.ok(response)
    }
    
    // Admin: Get ALL activities from ALL dealers
    @GetMapping("/activities")
    fun getAllActivities(
        @RequestParam(required = false) activityType: String?,
        @RequestParam(required = false) activityTypes: String?,
        @RequestParam(required = false) dealerId: String?,
        @RequestParam(required = false) dealerIds: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int
    ): ResponseEntity<ActivityService.ActivityListResponse> {
        val typesFromCsv = (activityTypes ?: "")
            .split(",")
            .mapNotNull { it.trim().takeIf { s -> s.isNotBlank() } }
        val dealerIdsFromCsv = (dealerIds ?: "")
            .split(",")
            .mapNotNull { it.trim().takeIf { s -> s.isNotBlank() } }

        val mergedTypes = (typesFromCsv + listOfNotNull(activityType?.trim()?.takeIf { it.isNotBlank() })).distinct()
        val mergedDealerIds = (dealerIdsFromCsv + listOfNotNull(dealerId?.trim()?.takeIf { it.isNotBlank() })).distinct()

        val response = activityService.getAllActivitiesForAdmin(
            activityTypes = mergedTypes,
            dealerIds = mergedDealerIds,
            search = q,
            page = page,
            size = pageSize
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/activities/activity-types")
    fun getActivityTypesForAdmin(): ResponseEntity<ActivityService.ActivityTypesResponse> {
        val response = activityService.getActivityTypesForAdmin()
        return ResponseEntity.ok(response)
    }
    
    // Admin: Send command directly by FCM token (no customer/device required)
    data class SendCommandByTokenRequestDto(
        @field:NotBlank(message = "FCM token is required")
        val fcmToken: String,
        @field:NotBlank(message = "Command type is required")
        val commandType: String,
        val commandData: String? = null
    )

    @PostMapping("/commands/send-by-token")
    fun sendCommandByFcmToken(
        @Valid @RequestBody request: SendCommandByTokenRequestDto
    ): ResponseEntity<DeviceCommandService.SendCommandByTokenResponse> {
        val response = deviceCommandService.sendCommandByFcmToken(
            DeviceCommandService.SendCommandByTokenRequest(
                fcmToken = request.fcmToken.trim(),
                commandType = request.commandType.trim(),
                commandData = request.commandData?.trim().takeIf { it?.isNotBlank() == true }
            )
        )
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
        }
    }

    // Admin: Get all FCM commands (for monitoring)
    @GetMapping("/commands")
    fun getAllCommands(
        @RequestParam(required = false) customerId: String?,
        @RequestParam(required = false) commandType: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") pageSize: Int
    ): ResponseEntity<Map<String, Any>> {
        val pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        
        val allCommands = deviceCommandRepository.findAll(pageable)
        
        // Apply filters
        val filteredCommands = allCommands.content.filter { command ->
            (customerId == null || command.customerId == customerId) &&
            (commandType == null || command.commandType == commandType) &&
            (status == null || command.status.name.equals(status, ignoreCase = true))
        }
        
        // Map to response format
        val commandsData = filteredCommands.map { command ->
            mapOf(
                "id" to (command.id ?: 0),
                "deviceId" to command.deviceId,
                "customerId" to command.customerId,
                "commandType" to command.commandType,
                "commandData" to command.commandData,
                "status" to command.status.name,
                "executedAt" to command.executedAt?.toString(),
                "errorMessage" to command.errorMessage,
                "expiryAt" to command.expiryAt?.toString(),
                "retryCount" to command.retryCount,
                "lastRetryAt" to command.lastRetryAt?.toString(),
                "nextRetryAt" to command.nextRetryAt?.toString(),
                "createdAt" to command.createdAt.toString(),
                "updatedAt" to command.updatedAt.toString()
            )
        }
        
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "commands" to commandsData,
            "total" to allCommands.totalElements,
            "page" to page,
            "pageSize" to pageSize,
            "totalPages" to allCommands.totalPages
        ))
    }
    
    // Dealer Command History
    @GetMapping("/dealers/{dealerId}/commands")
    fun getDealerCommands(
        @PathVariable dealerId: String,
        @RequestParam(required = false) customerId: String?,
        @RequestParam(required = false) commandType: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<CommandHistoryService.CommandHistoryResponse> {
        val response = commandHistoryService.getCommandHistory(
            dealerId,
            customerId,
            commandType,
            status,
            page,
            pageSize
        )
        return ResponseEntity.ok(response)
    }
    
    // ==================== SUPPORT TICKETS ====================
    
    // Get all support tickets with filters
    @GetMapping("/support/tickets")
    fun getAllTickets(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) priority: String?,
        @RequestParam(required = false) dealerId: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<SupportTicketService.TicketListResponse> {
        val response = supportTicketService.getAllTickets(status, priority, dealerId, search, page, pageSize)
        return ResponseEntity.ok(response)
    }
    
    // Get ticket statistics
    @GetMapping("/support/tickets/stats")
    fun getTicketStats(): ResponseEntity<SupportTicketService.TicketStatsResponse> {
        val response = supportTicketService.getTicketStats()
        return ResponseEntity.ok(response)
    }
    
    // Get a single ticket by ID
    @GetMapping("/support/tickets/{ticketId}")
    fun getTicket(@PathVariable ticketId: String): ResponseEntity<SupportTicketService.TicketResponse> {
        val response = supportTicketService.getTicket(ticketId)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(404).body(response)
        }
    }
    
    // Create a new support ticket
    data class CreateTicketRequestDto(
        @field:NotBlank(message = "Dealer ID is required")
        val dealerId: String,
        @field:NotBlank(message = "Subject is required")
        val subject: String,
        val description: String? = null,
        val category: String? = null,
        val priority: String = "medium"
    )
    
    @PostMapping("/support/tickets")
    fun createTicket(
        @RequestAttribute(value = "adminId", required = false) adminId: String?,
        @Valid @RequestBody request: CreateTicketRequestDto
    ): ResponseEntity<SupportTicketService.TicketResponse> {
        val admin = adminId ?: "admin"
        val response = supportTicketService.createTicket(
            admin,
            SupportTicketService.CreateTicketRequest(
                dealerId = request.dealerId,
                subject = request.subject,
                description = request.description,
                category = request.category,
                priority = request.priority
            )
        )
        return if (response.success) {
            ResponseEntity.status(201).body(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    // Update ticket
    data class UpdateTicketRequestDto(
        val subject: String? = null,
        val description: String? = null,
        val category: String? = null,
        val priority: String? = null,
        val status: String? = null,
        val assignedTo: String? = null
    )
    
    @PutMapping("/support/tickets/{ticketId}")
    fun updateTicket(
        @PathVariable ticketId: String,
        @Valid @RequestBody request: UpdateTicketRequestDto
    ): ResponseEntity<SupportTicketService.TicketResponse> {
        val response = supportTicketService.updateTicket(
            ticketId,
            SupportTicketService.UpdateTicketRequest(
                subject = request.subject,
                description = request.description,
                category = request.category,
                priority = request.priority,
                status = request.status,
                assignedTo = request.assignedTo
            )
        )
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    // Add message to ticket
    data class AddMessageRequestDto(
        @field:NotBlank(message = "Message is required")
        val message: String,
        val senderName: String? = null
    )
    
    @PostMapping(value = ["/support/tickets/{ticketId}/messages"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun addMessage(
        @PathVariable ticketId: String,
        @RequestAttribute(value = "adminId", required = false) adminId: String?,
        @Valid @RequestBody request: AddMessageRequestDto
    ): ResponseEntity<SupportTicketService.TicketResponse> {
        val ticketRes = supportTicketService.getTicket(ticketId)
        if (ticketRes.success && ticketRes.ticket != null && ticketRes.ticket!!.status.equals("closed", ignoreCase = true)) {
            return ResponseEntity.status(400).body(
                SupportTicketService.TicketResponse(success = false, message = "Cannot reply to a closed ticket")
            )
        }
        val admin = adminId ?: "admin"
        val response = supportTicketService.addMessage(
            ticketId,
            admin,
            com.da_emi_locker.backend.entity.SenderType.admin,
            SupportTicketService.AddMessageRequest(
                message = request.message,
                senderName = request.senderName ?: "Admin"
            )
        )
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    /** Admin reply with optional attachment (image or PDF, max 10MB). multipart: message (required), attachment (optional). */
    @PostMapping(value = ["/support/tickets/{ticketId}/messages"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addMessageWithAttachment(
        @PathVariable ticketId: String,
        @RequestAttribute(value = "adminId", required = false) adminId: String?,
        @RequestParam("message") message: String,
        @RequestParam(value = "attachment", required = false) attachment: MultipartFile?
    ): ResponseEntity<SupportTicketService.TicketResponse> {
        if (message.isBlank()) {
            return ResponseEntity.status(400).body(
                SupportTicketService.TicketResponse(success = false, message = "Message is required")
            )
        }
        val ticketRes = supportTicketService.getTicket(ticketId)
        if (ticketRes.success && ticketRes.ticket != null && ticketRes.ticket!!.status.equals("closed", ignoreCase = true)) {
            return ResponseEntity.status(400).body(
                SupportTicketService.TicketResponse(success = false, message = "Cannot reply to a closed ticket")
            )
        }
        var attachmentUrl: String? = null
        var attachmentFileName: String? = null
        attachment?.let { file ->
            if (!file.isEmpty) {
                if (file.size > 10 * 1024 * 1024L) {
                    return ResponseEntity.status(400).body(
                        SupportTicketService.TicketResponse(success = false, message = "Attachment must be under 10MB")
                    )
                }
                val contentType = file.contentType?.lowercase() ?: ""
                if (!contentType.startsWith("image/") && contentType != "application/pdf") {
                    return ResponseEntity.status(400).body(
                        SupportTicketService.TicketResponse(success = false, message = "Attachment must be image or PDF")
                    )
                }
                try {
                    attachmentUrl = s3StorageService.uploadImage(file, "ticket_attachments")
                        ?: throw IllegalStateException("S3 storage is not configured")
                    attachmentFileName = file.originalFilename?.take(255)
                } catch (e: Exception) {
                    return ResponseEntity.status(500).body(
                        SupportTicketService.TicketResponse(success = false, message = "Error uploading: ${e.message}")
                    )
                }
            }
        }
        val admin = adminId ?: "admin"
        val response = supportTicketService.addMessage(
            ticketId,
            admin,
            com.da_emi_locker.backend.entity.SenderType.admin,
            SupportTicketService.AddMessageRequest(
                message = message.trim(),
                senderName = "Admin",
                attachmentUrl = attachmentUrl,
                attachmentFileName = attachmentFileName
            )
        )
        return if (response.success) ResponseEntity.ok(response) else ResponseEntity.status(400).body(response)
    }
    
    // Update ticket status (quick action)
    data class UpdateStatusRequestDto(
        @field:NotBlank(message = "Status is required")
        val status: String
    )
    
    @PutMapping("/support/tickets/{ticketId}/status")
    fun updateTicketStatus(
        @PathVariable ticketId: String,
        @Valid @RequestBody request: UpdateStatusRequestDto
    ): ResponseEntity<SupportTicketService.TicketResponse> {
        val response = supportTicketService.updateTicket(
            ticketId,
            SupportTicketService.UpdateTicketRequest(status = request.status)
        )
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    // Delete ticket
    @DeleteMapping("/support/tickets/{ticketId}")
    fun deleteTicket(@PathVariable ticketId: String): ResponseEntity<SupportTicketService.TicketResponse> {
        val response = supportTicketService.deleteTicket(ticketId)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(404).body(response)
        }
    }
    
    // Get tickets for a specific dealer
    @GetMapping("/dealers/{dealerId}/tickets")
    fun getDealerTickets(
        @PathVariable dealerId: String,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<SupportTicketService.TicketListResponse> {
        val response = supportTicketService.getAllTickets(status, null, dealerId, null, page, pageSize)
        return ResponseEntity.ok(response)
    }
}

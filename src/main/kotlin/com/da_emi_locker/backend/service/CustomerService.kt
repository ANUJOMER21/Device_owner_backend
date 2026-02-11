package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.Activity
import com.da_emi_locker.backend.entity.Customer
import com.da_emi_locker.backend.entity.CustomerStatus
import com.da_emi_locker.backend.entity.Dealer
import com.da_emi_locker.backend.repository.AadharDetailsRepository
import com.da_emi_locker.backend.repository.ActivityRepository
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DealerRepository
import com.da_emi_locker.backend.repository.DeviceCommandRepository
import com.da_emi_locker.backend.repository.DeviceStatusRepository
import com.da_emi_locker.backend.repository.LoanDetailsRepository
import com.da_emi_locker.backend.repository.PANDetailsRepository
import com.da_emi_locker.backend.repository.PaymentHistoryRepository
import com.da_emi_locker.backend.repository.SimDetailsRepository
import com.da_emi_locker.backend.repository.ToggleStateRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class CustomerService(
    private val customerRepository: CustomerRepository,
    private val dealerRepository: DealerRepository,
    private val activityRepository: ActivityRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val toggleStateRepository: ToggleStateRepository,
    private val deviceCommandRepository: DeviceCommandRepository,
    private val simDetailsRepository: SimDetailsRepository,
    private val paymentHistoryRepository: PaymentHistoryRepository,
    private val loanDetailsRepository: LoanDetailsRepository,
    private val panDetailsRepository: PANDetailsRepository,
    private val aadharDetailsRepository: AadharDetailsRepository,
    private val salesExecutiveRepository: com.da_emi_locker.backend.repository.SalesExecutiveRepository
) {
    
    data class CreateCustomerRequest(
        val name: String,
        val email: String? = null,
        val phone: String,
        val address: String? = null,
        val customerImageUrl: String? = null,
        val signatureImageUrl: String? = null,
        val imei1: String,
        val imei2: String? = null
    )
    
    data class UpdateCustomerRequest(
        val name: String? = null,
        val email: String? = null,
        val phone: String? = null,
        val address: String? = null,
        val status: String? = null,
        val customerImageUrl: String? = null,
        val signatureImageUrl: String? = null
    )
    
    data class CustomerResponse(
        val success: Boolean,
        val message: String,
        val customer: CustomerData? = null,
        /** When true, REMOVE_DEVICE_OWNER was sent; caller should send the command; customer will be deleted after device verification. */
        val pendingVerification: Boolean = false
    )
    
    data class CustomerListResponse(
        val success: Boolean,
        val message: String,
        val customers: List<CustomerData>? = null,
        val total: Long? = null,
        val page: Int? = null,
        val pageSize: Int? = null,
        val totalPages: Int? = null
    )
    
    data class CustomerData(
        val customerId: String,
        val dealerId: String,
        val name: String,
        val email: String?,
        val phone: String,
        val address: String?,
        val status: String,
        val createdAt: String,
        val updatedAt: String,
        val fcmToken: String? = null,
        val customerImageUrl: String? = null,
        val signatureImageUrl: String? = null,
        val deviceStatus: DeviceStatusInfo? = null,
        val toggleStates: Map<String, Boolean>? = null,
        val aadharStatus: String? = null,
        val panStatus: String? = null,
        val loanStatus: String? = null,
        val imei1: String,
        val imei2: String? = null,
        val offlineUnlockCode: String? = null,
        val devicePassword: String? = null,
        val smsSecretKey: String? = null,
        /** Sales executive who added this customer (null if dealer added directly) */
        val salesExecutiveId: String? = null,
        val salesExecutiveName: String? = null
    )
    
    data class DeviceStatusInfo(
        val deviceId: String?,
        val deviceName: String?,
        val deviceType: String?,
        val status: String?,
        val isLocked: Boolean,
        val batteryLevel: Int?,
        val signalStrength: Int?,
        val lastSeen: String?,
        val latitude: java.math.BigDecimal? = null,
        val longitude: java.math.BigDecimal? = null,
        val deviceManufacturer: String? = null,
        val deviceModel: String? = null,
        val deviceBrand: String? = null,
        val androidVersion: String? = null,
        val sdkVersion: Int? = null,
        val serialNumber: String? = null
    )
    
    /** Response for configure app device activation */
    data class ActivateDeviceResponse(
        val success: Boolean,
        val message: String,
        val customerId: String? = null,
        val dealerId: String? = null,
        val deviceId: String? = null,
        val offlineUnlockCode: String? = null,
        val smsSecretKey: String? = null
    )
    
    @Transactional
    fun createCustomer(dealerId: String, request: CreateCustomerRequest, salesExecutiveId: String? = null): CustomerResponse {
        // Check dealer exists and get customer limit
        val dealer = dealerRepository.findByDealerId(dealerId)
            .orElse(null) ?: return CustomerResponse(
                success = false,
                message = "Dealer not found"
            )

        // If created by a Sales Executive, check SE kit balance
        if (salesExecutiveId != null) {
            val se = salesExecutiveRepository.findBySalesExecutiveId(salesExecutiveId).orElse(null)
                ?: return CustomerResponse(success = false, message = "Sales executive not found")
            val seUsed = customerRepository.countBySalesExecutiveId(salesExecutiveId).toInt()
            val seAvailable = se.assignedKits - seUsed
            if (seAvailable <= 0) {
                return CustomerResponse(
                    success = false,
                    message = "No remaining kits. Used: $seUsed, Assigned: ${se.assignedKits}. Ask your dealer to assign more kits."
                )
            }
        }
        
        // Check remaining kits (balance) at dealer level
        val currentCustomerCount = customerRepository.countByDealerId(dealerId)
        val remainingKits = dealer.customerLimit - currentCustomerCount.toInt()
        if (remainingKits <= 0) {
            return CustomerResponse(
                success = false,
                message = if (dealer.customerLimit == 0) {
                    "No kits assigned to this dealer. Please contact admin to assign kits."
                } else {
                    "No remaining kits available. Current: $currentCustomerCount, Limit: ${dealer.customerLimit}. Please purchase more kits."
                }
            )
        }
        
        // Validate phone number
        if (!request.phone.matches(Regex("^[0-9]{10}$"))) {
            return CustomerResponse(
                success = false,
                message = "Invalid phone number format. Must be 10 digits."
            )
        }
        
        // Validate email if provided
        if (request.email != null && !request.email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))) {
            return CustomerResponse(
                success = false,
                message = "Invalid email format"
            )
        }
        
        // Validate IMEI1 (required and must be unique)
        if (request.imei1.isBlank()) {
            return CustomerResponse(
                success = false,
                message = "IMEI1 is required"
            )
        }
        
        // Validate IMEI format (typically 15 digits)
        if (!request.imei1.matches(Regex("^[0-9]{15}$"))) {
            return CustomerResponse(
                success = false,
                message = "IMEI1 must be exactly 15 digits"
            )
        }
        
        // Check if IMEI1 already exists - allow reuse if existing customer is uninstalled
        val existingByImei1 = customerRepository.findByImei(request.imei1).orElse(null)
        if (existingByImei1 != null) {
            if (existingByImei1.status != CustomerStatus.uninstalled) {
                return CustomerResponse(
                    success = false,
                    message = "IMEI1 already exists. Each IMEI must be unique."
                )
            }
            // Release IMEI from uninstalled customer so it can be reused
            if (existingByImei1.imei1 == request.imei1) {
                existingByImei1.imei1 = "RELEASED_${existingByImei1.customerId}_${Instant.now().toEpochMilli()}"
            }
            if (existingByImei1.imei2 == request.imei1) {
                existingByImei1.imei2 = null
            }
            existingByImei1.updatedAt = Instant.now()
            customerRepository.save(existingByImei1)
        }
        
        // Validate IMEI2 if provided
        if (request.imei2 != null && request.imei2.isNotBlank()) {
            if (!request.imei2.matches(Regex("^[0-9]{15}$"))) {
                return CustomerResponse(
                    success = false,
                    message = "IMEI2 must be exactly 15 digits if provided"
                )
            }
            
            // Check if IMEI2 already exists - allow reuse if existing customer is uninstalled
            val existingByImei2 = customerRepository.findByImei(request.imei2).orElse(null)
            if (existingByImei2 != null) {
                if (existingByImei2.status != CustomerStatus.uninstalled) {
                    return CustomerResponse(
                        success = false,
                        message = "IMEI2 already exists. Each IMEI must be unique."
                    )
                }
                // Release IMEI from uninstalled customer so it can be reused
                if (existingByImei2.imei1 == request.imei2) {
                    existingByImei2.imei1 = "RELEASED_${existingByImei2.customerId}_${Instant.now().toEpochMilli()}"
                }
                if (existingByImei2.imei2 == request.imei2) {
                    existingByImei2.imei2 = null
                }
                existingByImei2.updatedAt = Instant.now()
                customerRepository.save(existingByImei2)
            }
        }
        
        // Generate unique customer ID
        val customerId = generateCustomerId()
        
        // Create customer
        val customer = Customer().apply {
            this.customerId = customerId
            this.dealerId = dealerId
            this.name = request.name
            this.email = request.email
            this.phone = request.phone
            this.address = request.address
            this.status = CustomerStatus.pending_activation
            this.imei1 = request.imei1
            this.imei2 = request.imei2
            this.offlineUnlockCode = generateOfflineUnlockCode()
            this.customerImageUrl = request.customerImageUrl
            this.signatureImageUrl = request.signatureImageUrl
            this.salesExecutiveId = salesExecutiveId
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }
        
        val savedCustomer = customerRepository.save(customer)
        
        // Log activity
        val activity = Activity().apply {
            this.customerId = savedCustomer.customerId
            this.deviceId = null
            this.activityType = "customer_added"
            this.activityDescription = "Customer ${savedCustomer.name} added by dealer"
            this.createdAt = Instant.now()
        }
        activityRepository.save(activity)
        
        return CustomerResponse(
            success = true,
            message = "Customer created successfully",
            customer = buildCustomerData(savedCustomer)
        )
    }
    
    fun getCustomers(
        dealerId: String,
        status: String? = null,
        page: Int = 0,
        size: Int = 20
    ): CustomerListResponse {
        val pageable: Pageable = PageRequest.of(page, size)
        val customers: Page<Customer>
        
        if (status != null) {
            val customerStatus = try {
                CustomerStatus.valueOf(status.lowercase())
            } catch (e: IllegalArgumentException) {
                return CustomerListResponse(
                    success = false,
                    message = "Invalid status: $status"
                )
            }
            customers = customerRepository.findByDealerIdAndStatus(dealerId, customerStatus, pageable)
        } else {
            customers = customerRepository.findByDealerId(dealerId, pageable)
        }
        
        // Get device statuses for all customers in this page
        val customerIds = customers.content.map { it.customerId }
        val allCustomerDevices = if (customerIds.isNotEmpty()) {
            deviceStatusRepository.findByCustomerIdIn(customerIds)
        } else {
            emptyList()
        }
        val deviceMap = allCustomerDevices.associateBy { it.customerId }
        
        val customerDataList = customers.content.map { customer ->
            val device = deviceMap[customer.customerId]
            val deviceStatusInfo = device?.let {
                val isLocked = checkDeviceLocked(it.deviceId)
                DeviceStatusInfo(
                    deviceId = it.deviceId,
                    deviceName = it.deviceName,
                    deviceType = it.deviceType,
                    status = it.status.name,
                    isLocked = isLocked,
                    batteryLevel = it.batteryLevel,
                    signalStrength = it.signalStrength,
                    lastSeen = it.lastSeen?.toString(),
                    latitude = it.latitude,
                    longitude = it.longitude,
                    deviceManufacturer = it.deviceManufacturer,
                    deviceModel = it.deviceModel,
                    deviceBrand = it.deviceBrand,
                    androidVersion = it.androidVersion,
                    sdkVersion = it.sdkVersion,
                    serialNumber = it.serialNumber
                )
            }
            
            buildCustomerData(customer, deviceStatusInfo)
        }
        
        return CustomerListResponse(
            success = true,
            message = "Customers retrieved successfully",
            customers = customerDataList,
            total = customers.totalElements,
            page = customers.number,
            pageSize = customers.size,
            totalPages = customers.totalPages
        )
    }
    
    fun getCustomer(customerId: String, dealerId: String): CustomerResponse {
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return CustomerResponse(
                success = false,
                message = "Customer not found"
            )
        
        // Verify customer belongs to dealer
        if (customer.dealerId != dealerId) {
            return CustomerResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        // Get device status
        val devices = deviceStatusRepository.findByCustomerId(customerId)
        val device = devices.firstOrNull()
        
        val deviceStatusInfo = device?.let {
            val isLocked = checkDeviceLocked(it.deviceId)
            DeviceStatusInfo(
                deviceId = it.deviceId,
                deviceName = it.deviceName,
                deviceType = it.deviceType,
                status = it.status.name,
                isLocked = isLocked,
                batteryLevel = it.batteryLevel,
                signalStrength = it.signalStrength,
                lastSeen = it.lastSeen?.toString(),
                latitude = it.latitude,
                longitude = it.longitude,
                deviceManufacturer = it.deviceManufacturer,
                deviceModel = it.deviceModel,
                deviceBrand = it.deviceBrand,
                androidVersion = it.androidVersion,
                sdkVersion = it.sdkVersion,
                serialNumber = it.serialNumber
            )
        }
        
        // Get toggle states (only needed toggles)
        val toggleStates = if (device != null) {
            val toggles = toggleStateRepository.findByDeviceId(device.deviceId)
            val toggleMap = toggles.associate { it.toggleType to it.state }
            val validToggleTypes = setOf("device_lock", "block_apps")
            validToggleTypes.associateWith { toggleType ->
                toggleMap[toggleType] ?: false
            }
        } else {
            null
        }
        
        return CustomerResponse(
            success = true,
            message = "Customer retrieved successfully",
            customer = buildCustomerData(customer, deviceStatusInfo, toggleStates)
        )
    }
    
    /** Build CustomerData from entity, optionally including device status and toggle states */
    private fun buildCustomerData(
        customer: Customer,
        deviceStatus: DeviceStatusInfo? = null,
        toggleStates: Map<String, Boolean>? = null
    ): CustomerData {
        val seName = customer.salesExecutiveId?.let { seId ->
            salesExecutiveRepository.findBySalesExecutiveId(seId).orElse(null)?.name
        }
        return CustomerData(
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
            deviceStatus = deviceStatus,
            toggleStates = toggleStates,
            aadharStatus = customer.aadharStatus,
            panStatus = customer.panStatus,
            loanStatus = customer.loanStatus,
            imei1 = customer.imei1.orEmpty(),
            imei2 = customer.imei2,
            offlineUnlockCode = customer.offlineUnlockCode,
            devicePassword = customer.devicePassword,
            smsSecretKey = customer.smsSecretKey,
            salesExecutiveId = customer.salesExecutiveId,
            salesExecutiveName = seName
        )
    }

    private fun checkDeviceLocked(deviceId: String): Boolean {
        val lockCommands = deviceCommandRepository.findByDeviceId(deviceId)
            .filter { 
                it.commandType == DeviceCommandService.CommandAction.LOCK_DEVICE.value && 
                it.status in listOf(
                    com.da_emi_locker.backend.entity.CommandStatus.pending,
                    com.da_emi_locker.backend.entity.CommandStatus.sent,
                    com.da_emi_locker.backend.entity.CommandStatus.executing,
                    com.da_emi_locker.backend.entity.CommandStatus.executed
                )
            }
        return lockCommands.isNotEmpty()
    }
    
    @Transactional
    fun updateCustomer(customerId: String, dealerId: String, request: UpdateCustomerRequest): CustomerResponse {
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return CustomerResponse(
                success = false,
                message = "Customer not found"
            )
        
        // Verify customer belongs to dealer
        if (customer.dealerId != dealerId) {
            return CustomerResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        // Validate email if provided
        if (request.email != null && !request.email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))) {
            return CustomerResponse(
                success = false,
                message = "Invalid email format"
            )
        }
        
        // Validate phone if provided
        if (request.phone != null && !request.phone.matches(Regex("^[0-9]{10}$"))) {
            return CustomerResponse(
                success = false,
                message = "Invalid phone number format. Must be 10 digits."
            )
        }
        
        // Uninstalled status cannot be changed by admin/dealer via update — only via mark-uninstalled
        if (customer.status == CustomerStatus.uninstalled) {
            request.status?.let { requested ->
                val requestedStatus = try { CustomerStatus.valueOf(requested.lowercase()) } catch (_: IllegalArgumentException) { null }
                if (requestedStatus != null && requestedStatus != CustomerStatus.uninstalled) {
                    return CustomerResponse(
                        success = false,
                        message = "Cannot change status of uninstalled customer"
                    )
                }
            }
        }

        // Update fields
        request.name?.let { customer.name = it }
        request.email?.let { customer.email = it }
        request.phone?.let { customer.phone = it }
        request.address?.let { customer.address = it }
        request.customerImageUrl?.let { customer.customerImageUrl = it }
        request.signatureImageUrl?.let { customer.signatureImageUrl = it }
        request.status?.let {
            try {
                val newStatus = CustomerStatus.valueOf(it.lowercase())
                if (customer.status == CustomerStatus.uninstalled && newStatus != CustomerStatus.uninstalled) {
                    return CustomerResponse(success = false, message = "Cannot change status of uninstalled customer")
                }
                customer.status = newStatus
            } catch (e: IllegalArgumentException) {
                return CustomerResponse(
                    success = false,
                    message = "Invalid status: $it"
                )
            }
        }
        
        customer.updatedAt = Instant.now()
        val savedCustomer = customerRepository.save(customer)
        
        // Log activity
        val activity = Activity().apply {
            this.customerId = savedCustomer.customerId
            this.deviceId = null
            this.activityType = "customer_updated"
            this.activityDescription = "Customer ${savedCustomer.name} updated"
            this.createdAt = Instant.now()
        }
        activityRepository.save(activity)
        
        return CustomerResponse(
            success = true,
            message = "Customer updated successfully",
            customer = buildCustomerData(savedCustomer)
        )
    }
    
    /**
     * Mark customer as uninstalled (no FCM command sent).
     * Used when dealer/admin chooses "Uninstall" — only updates status; command section is disabled for this customer.
     */
    @Transactional
    fun markCustomerUninstalled(customerId: String, dealerId: String): CustomerResponse {
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return CustomerResponse(success = false, message = "Customer not found")
        if (customer.dealerId != dealerId) {
            return CustomerResponse(success = false, message = "Access denied")
        }
        if (customer.status == CustomerStatus.uninstalled) {
            return CustomerResponse(success = true, message = "Customer already marked as uninstalled", customer = null)
        }
        customer.status = CustomerStatus.uninstalled
        customer.imei1 = null
        customer.imei2 = null
        customer.fcmToken = null
        customer.updatedAt = Instant.now()
        customerRepository.save(customer)
        // Remove device_status rows for this customer (cascades to device_commands, toggle_states per DB)
        val deviceStatuses = deviceStatusRepository.findByCustomerId(customerId)
        if (deviceStatuses.isNotEmpty()) {
            deviceStatusRepository.deleteAll(deviceStatuses)
        }
        val now = Instant.now()
        val activity = Activity().apply {
            this.customerId = customerId
            this.deviceId = null
            this.activityType = "customer_marked_uninstalled"
            this.activityDescription = "Customer marked as uninstalled (no command sent)"
            this.createdAt = now
            this.updatedAt = now
        }
        activityRepository.save(activity)
        return CustomerResponse(
            success = true,
            message = "Customer marked as uninstalled. Command section is now disabled.",
            customer = buildCustomerData(customer)
        )
    }

    /**
     * Delete customer and all related data; reverses one kit to dealer (used count decreases automatically).
     * Call this after REMOVE_DEVICE_OWNER verification when pendingDeletion is set, or when customer is not installed/active.
     */
    @Transactional
    fun performCascadeDelete(customerId: String, dealerId: String): CustomerResponse {
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return CustomerResponse(
                success = false,
                message = "Customer not found"
            )
        if (customer.dealerId != dealerId) {
            return CustomerResponse(success = false, message = "Access denied")
        }
        val customerName = customer.name
        val deviceIds = deviceStatusRepository.findByCustomerId(customerId).map { it.deviceId }
        // Delete toggle states for this customer's devices
        deviceIds.forEach { deviceId ->
            val toggles = toggleStateRepository.findByDeviceId(deviceId)
            if (toggles.isNotEmpty()) toggleStateRepository.deleteAll(toggles)
        }
        deviceCommandRepository.findByCustomerId(customerId).let { if (it.isNotEmpty()) deviceCommandRepository.deleteAll(it) }
        activityRepository.findByCustomerId(customerId).let { if (it.isNotEmpty()) activityRepository.deleteAll(it) }
        deviceStatusRepository.findByCustomerId(customerId).let { if (it.isNotEmpty()) deviceStatusRepository.deleteAll(it) }
        simDetailsRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "createdAt"))).let { if (it.isNotEmpty()) simDetailsRepository.deleteAll(it) }
        paymentHistoryRepository.findByCustomerId(customerId).let { if (it.isNotEmpty()) paymentHistoryRepository.deleteAll(it) }
        loanDetailsRepository.findByCustomerId(customerId).let { if (it.isNotEmpty()) loanDetailsRepository.deleteAll(it) }
        panDetailsRepository.findByCustomerId(customerId).ifPresent { panDetailsRepository.delete(it) }
        aadharDetailsRepository.findByCustomerId(customerId).ifPresent { aadharDetailsRepository.delete(it) }
        customerRepository.delete(customer)
        return CustomerResponse(success = true, message = "Customer $customerName and all related data deleted. One kit returned to dealer.")
    }

    /**
     * Admin/dealer delete customer. If customer is installed or active, sets pendingDeletion and returns pendingVerification;
     * caller must send REMOVE_DEVICE_OWNER; after device verification we call performCascadeDelete.
     * Otherwise performs cascade delete immediately (all related data deleted, one kit effectively returned to dealer).
     */
    @Transactional
    fun deleteCustomer(customerId: String, dealerId: String): CustomerResponse {
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return CustomerResponse(
                success = false,
                message = "Customer not found"
            )
        if (customer.dealerId != dealerId) {
            return CustomerResponse(success = false, message = "Access denied")
        }
        val isInstalledOrActive = customer.status == CustomerStatus.installed || customer.status == CustomerStatus.active
        if (isInstalledOrActive) {
            customer.pendingDeletion = true
            customer.updatedAt = Instant.now()
            customerRepository.save(customer)
            return CustomerResponse(
                success = true,
                message = "REMOVE_DEVICE_OWNER will be sent to the device. Customer will be deleted after device verification. One kit will be returned to the dealer.",
                pendingVerification = true
            )
        }
        return performCascadeDelete(customerId, dealerId)
    }
    
    /**
     * Activate device from configure app: find customer by IMEI,
     * create/update device_status, set customer status to installed if pending, save FCM token.
     * If customer is already active/installed, updates FCM token and device status (last seen, device id) without error.
     * Works with deviceId (from DPM/provisioning) or falls back to IMEI as device_id.
     */
    @Transactional
    fun activateDevice(
        deviceId: String?,
        imei: String,
        fcmToken: String,
        phoneDetails: Map<String, String?>? = null
    ): ActivateDeviceResponse {
        if (imei.isBlank()) {
            return ActivateDeviceResponse(success = false, message = "IMEI is required")
        }
        val customer = customerRepository.findByImei(imei)
            .orElse(null) ?: return ActivateDeviceResponse(
                success = false,
                message = "No customer found for this IMEI. Ensure the customer is added by dealer first."
            )
        val isFirstActivation = customer.status == CustomerStatus.pending_activation
        val effectiveDeviceId = deviceId?.takeIf { it.isNotBlank() } ?: imei
        if (isFirstActivation) {
            customer.status = CustomerStatus.installed
        }
        customer.fcmToken = fcmToken
        // Generate SMS secret key for offline lock/unlock via SMS if not already set
        if (customer.smsSecretKey.isNullOrBlank()) {
            customer.smsSecretKey = generateSmsSecretKey()
        }
        customer.updatedAt = Instant.now()
        customerRepository.save(customer)
        
        // Check if this deviceId is already linked to another customer
        val existingByDeviceId = deviceStatusRepository.findByDeviceId(effectiveDeviceId).orElse(null)
        if (existingByDeviceId != null && existingByDeviceId.customerId != customer.customerId) {
            // Device is owned by a different customer - allow reassignment only when that customer is uninstalled
            val previousOwner = customerRepository.findByCustomerId(existingByDeviceId.customerId).orElse(null)
            if (previousOwner == null || previousOwner.status != CustomerStatus.uninstalled) {
                return ActivateDeviceResponse(
                    success = false,
                    message = "This device is already in use by another customer. Activation is allowed only when the previous customer's app is uninstalled."
                )
            }
            // Previous owner is uninstalled: reassign this device to the current customer
            existingByDeviceId.customerId = customer.customerId
            existingByDeviceId.deviceName = "Configure App Device"
            existingByDeviceId.lastSeen = Instant.now()
            existingByDeviceId.updatedAt = Instant.now()
            existingByDeviceId.status = com.da_emi_locker.backend.entity.DeviceStatusEnum.online
            applyPhoneDetails(existingByDeviceId, phoneDetails)
            deviceStatusRepository.save(existingByDeviceId)
        } else {
            // Device not taken by another customer: use or create record for this customer
            val byCustomer = deviceStatusRepository.findByCustomerId(customer.customerId)
            if (byCustomer.isNotEmpty()) {
                val first = byCustomer.first()
                first.deviceId = effectiveDeviceId
                first.lastSeen = Instant.now()
                first.updatedAt = Instant.now()
                first.status = com.da_emi_locker.backend.entity.DeviceStatusEnum.online
                applyPhoneDetails(first, phoneDetails)
                deviceStatusRepository.save(first)
            } else {
                val newDevice = com.da_emi_locker.backend.entity.DeviceStatus().apply {
                    this.deviceId = effectiveDeviceId
                    this.customerId = customer.customerId
                    this.deviceName = "Configure App Device"
                    this.deviceType = "Android"
                    this.status = com.da_emi_locker.backend.entity.DeviceStatusEnum.online
                    this.lastSeen = Instant.now()
                    this.createdAt = Instant.now()
                    this.updatedAt = Instant.now()
                }
                applyPhoneDetails(newDevice, phoneDetails)
                deviceStatusRepository.save(newDevice)
            }
        }
        val activity = Activity().apply {
            this.customerId = customer.customerId
            this.deviceId = effectiveDeviceId
            this.activityType = "device_activated"
            this.activityDescription = "Device activated via configure app"
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }
        activityRepository.save(activity)
        return ActivateDeviceResponse(
            success = true,
            message = "Device activated successfully",
            customerId = customer.customerId,
            dealerId = customer.dealerId,
            deviceId = effectiveDeviceId,
            offlineUnlockCode = customer.offlineUnlockCode,
            smsSecretKey = customer.smsSecretKey
        )
    }
    
    /** Lookup customer by IMEI (for configure app when customerId/OUC missing). Returns customerId and offlineUnlockCode. */
    fun getCustomerByImei(imei: String): ActivateDeviceResponse {
        if (imei.isBlank()) {
            return ActivateDeviceResponse(success = false, message = "IMEI is required")
        }
        val customer = customerRepository.findByImei(imei.trim())
            .orElse(null) ?: return ActivateDeviceResponse(
                success = false,
                message = "No customer found for this IMEI"
            )
        return ActivateDeviceResponse(
            success = true,
            message = "OK",
            customerId = customer.customerId,
            dealerId = customer.dealerId,
            deviceId = deviceStatusRepository.findByCustomerId(customer.customerId).firstOrNull()?.deviceId,
            offlineUnlockCode = customer.offlineUnlockCode
        )
    }
    
    /** Deregister device when app is uninstalled (REMOVE_DEVICE_OWNER). Clears IMEI and sets status to uninstalled. */
    @Transactional
    fun deregisterDevice(customerId: String, imei: String?): CustomerResponse {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return CustomerResponse(success = false, message = "Customer not found")
        if (imei != null && imei.isNotBlank()) {
            val match = customer.imei1 == imei.trim() || customer.imei2 == imei.trim()
            if (!match) {
                return CustomerResponse(success = false, message = "IMEI does not match customer")
            }
        }
        customer.imei1 = null
        customer.imei2 = null
        customer.fcmToken = null
        customer.status = CustomerStatus.uninstalled
        customer.updatedAt = Instant.now()
        customerRepository.save(customer)
        return CustomerResponse(success = true, message = "Device deregistered, IMEI cleared")
    }

    /** Update FCM token for configure app (no auth). Validates customerId and optional imei. */
    fun updateFCMTokenFromDevice(customerId: String, fcmToken: String, imei: String?): CustomerResponse {
        val customer = customerRepository.findByCustomerId(customerId).orElse(null)
            ?: return CustomerResponse(success = false, message = "Customer not found")
        if (imei != null && imei.isNotBlank()) {
            val match = customer.imei1 == imei.trim() || customer.imei2 == imei.trim()
            if (!match) {
                return CustomerResponse(success = false, message = "IMEI does not match customer")
            }
        }
        customer.fcmToken = fcmToken
        customer.updatedAt = Instant.now()
        customerRepository.save(customer)
        return CustomerResponse(success = true, message = "FCM token updated", customer = null)
    }
    
    @Transactional
    fun updateFCMToken(customerId: String, fcmToken: String): CustomerResponse {
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return CustomerResponse(
                success = false,
                message = "Customer not found"
            )
        
        customer.fcmToken = fcmToken
        customer.updatedAt = Instant.now()
        val savedCustomer = customerRepository.save(customer)
        
        // Check if device status exists, if not create one using IMEI1 (skip if IMEI cleared e.g. uninstalled)
        val devices = deviceStatusRepository.findByCustomerId(customerId)
        val deviceId = customer.imei1?.takeIf { it.isNotBlank() }
        if (devices.isEmpty() && deviceId != null) {
            
            // Check if device ID is already used by another customer (sanity check)
            val existingDevice = deviceStatusRepository.findByDeviceId(deviceId).orElse(null)
            
            if (existingDevice == null) {
                val newDevice = com.da_emi_locker.backend.entity.DeviceStatus().apply {
                    this.deviceId = deviceId
                    this.customerId = customer.customerId
                    this.deviceName = "${customer.name}'s Device"
                    this.deviceType = "Android"
                    this.status = com.da_emi_locker.backend.entity.DeviceStatusEnum.offline
                    this.createdAt = Instant.now()
                    this.updatedAt = Instant.now()
                }
                deviceStatusRepository.save(newDevice)
                
                // Log activity for device creation
                val activity = Activity().apply {
                    this.customerId = customer.customerId
                    this.deviceId = deviceId
                    this.activityType = "device_registered_auto"
                    this.activityDescription = "Device auto-registered on FCM update"
                    this.createdAt = Instant.now()
                }
                activityRepository.save(activity)
            } else if (existingDevice.customerId == customer.customerId) {
                // Already exists for this customer (shouldn't happen given isEmpty check above, but for safety)
                // Do nothing
            } else {
                // Device ID used by another customer - warn but don't fail the token update
                // Ideally log this
            }
        }
        
        return CustomerResponse(
            success = true,
            message = "FCM token updated successfully",
            customer = buildCustomerData(savedCustomer)
        )
    }

    @Transactional
    fun generateOfflineUnlockCodeIfMissing(customerId: String): CustomerResponse {
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return CustomerResponse(
                success = false,
                message = "Customer not found"
            )

        if (customer.offlineUnlockCode.isNullOrBlank()) {
            customer.offlineUnlockCode = generateOfflineUnlockCode()
            customer.updatedAt = Instant.now()
        }

        val savedCustomer = customerRepository.save(customer)

        return CustomerResponse(
            success = true,
            message = if (savedCustomer.offlineUnlockCode.isNullOrBlank()) {
                "Offline unlock code not generated"
            } else {
                "Offline unlock code generated successfully"
            },
            customer = buildCustomerData(savedCustomer)
        )
    }

    private fun generateOfflineUnlockCode(): String {
        // 10 chars alphanumeric upper-case. Retry a few times to guarantee uniqueness.
        repeat(10) {
            val code = UUID.randomUUID().toString()
                .replace("-", "")
                .uppercase()
                .take(10)
            if (!customerRepository.existsByOfflineUnlockCode(code)) return code
        }
        // fallback
        return UUID.randomUUID().toString().replace("-", "").uppercase().take(12)
    }
    
    private fun generateCustomerId(): String {
        val timestamp = System.currentTimeMillis().toString().takeLast(8)
        val random = UUID.randomUUID().toString().substring(0, 4).uppercase().replace("-", "")
        return "CUST$timestamp$random"
    }

    private fun generateSmsSecretKey(): String {
        return UUID.randomUUID().toString().replace("-", "").uppercase().take(32)
    }

    /** Apply phone details from activation request to device status entity */
    private fun applyPhoneDetails(
        device: com.da_emi_locker.backend.entity.DeviceStatus,
        phoneDetails: Map<String, String?>?
    ) {
        if (phoneDetails == null) return
        phoneDetails["manufacturer"]?.let { device.deviceManufacturer = it }
        phoneDetails["model"]?.let { device.deviceModel = it }
        phoneDetails["brand"]?.let { device.deviceBrand = it }
        phoneDetails["androidVersion"]?.let { device.androidVersion = it }
        phoneDetails["sdkVersion"]?.let { v -> v.toIntOrNull()?.let { device.sdkVersion = it } }
        phoneDetails["serialNumber"]?.let { device.serialNumber = it }
        // Also update deviceName with brand + model
        val brand = phoneDetails["brand"] ?: ""
        val model = phoneDetails["model"] ?: ""
        if (brand.isNotBlank() || model.isNotBlank()) {
            device.deviceName = "$brand $model".trim()
        }
    }
}

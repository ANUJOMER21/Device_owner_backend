package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.CustomerStatus
import com.da_emi_locker.backend.entity.DeviceStatusEnum
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DealerRepository
import com.da_emi_locker.backend.repository.DeviceStatusRepository
import com.da_emi_locker.backend.repository.ToggleStateRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class AdminDealerService(
    private val dealerRepository: DealerRepository,
    private val customerRepository: CustomerRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val toggleStateRepository: ToggleStateRepository
) {
    
    data class DealerDashboardResponse(
        val success: Boolean,
        val message: String,
        val stats: DealerDashboardStats? = null
    )
    
    data class DealerDashboardStats(
        val dealerId: String,
        val dealerName: String,
        val customerLimit: Int,
        val balance: Int, // Available kits (limit - used)
        val totalCustomers: Long,
        val activeCustomers: Long,
        val pendingActivation: Long,
        val installed: Long,
        val uninstalled: Long,
        val lockedDevices: Long,
        val unlockedDevices: Long
    )
    
    fun getDealerDashboard(dealerId: String): DealerDashboardResponse {
        val dealer = dealerRepository.findByDealerId(dealerId)
            .orElse(null) ?: return DealerDashboardResponse(
                success = false,
                message = "Dealer not found"
            )
        
        // Get customer statistics
        val totalCustomers = customerRepository.countByDealerId(dealerId)
        val dealerCustomers = customerRepository.findByDealerId(dealerId)
        
        val activeCustomers = dealerCustomers.count { it.status == CustomerStatus.active || it.status == CustomerStatus.installed }.toLong()
        val pendingActivation = dealerCustomers.count { it.status == CustomerStatus.pending_activation }.toLong()
        
        // Get customer IDs
        val customerIds = dealerCustomers.map { it.customerId }
        
        // Get devices for these customers (for device stats only)
        val totalDevices = if (customerIds.isNotEmpty()) {
            deviceStatusRepository.findByCustomerIdIn(customerIds).size.toLong()
        } else {
            0L
        }
        
        // Installed = device activated via configure app (active or installed)
        val installed = activeCustomers
        val uninstalled = dealerCustomers.count { it.status == CustomerStatus.uninstalled }.toLong()
        
        // Calculate locked/unlocked devices
        val lockedDevices = if (customerIds.isNotEmpty()) {
            deviceStatusRepository.countLockedDevicesByCustomerIds(customerIds)
        } else {
            0L
        }
        val unlockedDevices = totalDevices - lockedDevices
        
        // Calculate balance (available kits)
        val balance = dealer.customerLimit - totalCustomers.toInt()
        
        return DealerDashboardResponse(
            success = true,
            message = "Dashboard statistics retrieved successfully",
            stats = DealerDashboardStats(
                dealerId = dealer.dealerId,
                dealerName = dealer.name,
                customerLimit = dealer.customerLimit,
                balance = if (balance >= 0) balance else 0,
                totalCustomers = totalCustomers,
                activeCustomers = activeCustomers,
                pendingActivation = pendingActivation,
                installed = installed,
                uninstalled = uninstalled,
                lockedDevices = lockedDevices,
                unlockedDevices = unlockedDevices
            )
        )
    }
    
    fun getDealerCustomers(
        dealerId: String,
        page: Int = 0,
        pageSize: Int = 20
    ): CustomerService.CustomerListResponse {
        val pageable: Pageable = PageRequest.of(page, pageSize)
        val customers = customerRepository.findByDealerId(dealerId, pageable)
        
        // Map to customer data (similar to CustomerService)
        val customerDataList = customers.content.map { customer ->
            val device = deviceStatusRepository.findByCustomerId(customer.customerId).firstOrNull()
            val isLocked = device?.let { 
                toggleStateRepository.findByDeviceId(it.deviceId)
                    .any { toggle -> toggle.state == true && toggle.toggleType == "device_lock" }
            } ?: false
            
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
                deviceStatus = device?.let {
                    CustomerService.DeviceStatusInfo(
                        deviceId = it.deviceId,
                        deviceName = it.deviceName,
                        deviceType = it.deviceType,
                        status = it.status.name,
                        isLocked = isLocked,
                        batteryLevel = it.batteryLevel,
                        signalStrength = it.signalStrength,
                        lastSeen = it.lastSeen?.toString(),
                        latitude = it.latitude,
                        longitude = it.longitude
                    )
                },
                toggleStates = null,
                aadharStatus = customer.aadharStatus,
                panStatus = customer.panStatus,
                loanStatus = customer.loanStatus,
                imei1 = customer.imei1.orEmpty(),
                imei2 = customer.imei2,
                offlineUnlockCode = customer.offlineUnlockCode
            )
        }
        
        return CustomerService.CustomerListResponse(
            success = true,
            message = "Customers retrieved successfully",
            customers = customerDataList,
            total = customers.totalElements,
            page = customers.number,
            pageSize = customers.size,
            totalPages = customers.totalPages
        )
    }
}

package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.CustomerStatus
import com.da_emi_locker.backend.entity.DeviceStatusEnum
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DealerRepository
import com.da_emi_locker.backend.repository.DeviceStatusRepository
import org.springframework.stereotype.Service

@Service
class DashboardService(
    private val customerRepository: CustomerRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val dealerRepository: DealerRepository
) {
    
    data class DashboardStatsResponse(
        val success: Boolean,
        val message: String,
        val stats: DashboardStats? = null
    )
    
    data class DashboardStats(
        val totalCustomers: Long,
        val activeCustomers: Long,
        val uninstalledCustomers: Long,
        /** Total kits assigned to dealer (customer limit). */
        val customerLimit: Int,
        /** Kits remaining = customerLimit - totalCustomers. */
        val kitRemaining: Int,
        /** Customers not yet activated (pending_activation). */
        val pendingActivation: Long,
        /** Customers with status active or installed. */
        val installed: Long,
        /** Customers with status uninstalled. */
        val uninstalled: Long,
        val totalDevices: Long,
        val onlineDevices: Long,
        val offlineDevices: Long,
        val lockedDevices: Long,
        val unlockedDevices: Long
    )
    
    fun getDashboardStats(dealerId: String): DashboardStatsResponse {
        val dealer = dealerRepository.findByDealerId(dealerId).orElse(null)
        val customerLimit = dealer?.customerLimit ?: 0
        
        // Get customer statistics
        val totalCustomers = customerRepository.countByDealerId(dealerId)
        val dealerCustomers = customerRepository.findByDealerId(dealerId)
        val activeCustomers = dealerCustomers.count { it.status == CustomerStatus.active || it.status == CustomerStatus.installed }.toLong()
        val uninstalledCustomers = dealerCustomers.count { it.status == CustomerStatus.uninstalled }.toLong()
        val pendingActivation = dealerCustomers.count { it.status == CustomerStatus.pending_activation }.toLong()
        
        // Kit remaining = limit - used (total customers assigned)
        val kitRemaining = (customerLimit - totalCustomers.toInt()).coerceAtLeast(0)
        
        // Installed = device activated via configure app (active or installed status)
        val installed = activeCustomers
        val uninstalled = uninstalledCustomers
        
        // Get device statistics (devices linked to dealer's customers)
        val customerIds = dealerCustomers.map { it.customerId }
        
        val allDevices = if (customerIds.isNotEmpty()) {
            deviceStatusRepository.findByCustomerIdIn(customerIds)
        } else {
            emptyList()
        }
        
        val totalDevices = allDevices.size.toLong()
        val onlineDevices = allDevices.count { it.status == DeviceStatusEnum.online }.toLong()
        val offlineDevices = allDevices.count { it.status == DeviceStatusEnum.offline }.toLong()
        
        // Get locked/unlocked devices (from toggle_states)
        val lockedDevices = if (customerIds.isNotEmpty()) {
            deviceStatusRepository.countLockedDevicesByCustomerIds(customerIds)
        } else {
            0L
        }
        val unlockedDevices = totalDevices - lockedDevices
        
        return DashboardStatsResponse(
            success = true,
            message = "Dashboard statistics retrieved successfully",
            stats = DashboardStats(
                totalCustomers = totalCustomers,
                activeCustomers = activeCustomers,
                uninstalledCustomers = uninstalledCustomers,
                customerLimit = customerLimit,
                kitRemaining = kitRemaining,
                pendingActivation = pendingActivation,
                installed = installed,
                uninstalled = uninstalled,
                totalDevices = totalDevices,
                onlineDevices = onlineDevices,
                offlineDevices = offlineDevices,
                lockedDevices = lockedDevices,
                unlockedDevices = unlockedDevices
            )
        )
    }
}

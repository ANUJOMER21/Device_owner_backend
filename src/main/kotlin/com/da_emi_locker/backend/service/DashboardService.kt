package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.CustomerStatus
import com.da_emi_locker.backend.entity.DeviceStatusEnum
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DealerRepository
import com.da_emi_locker.backend.repository.DeviceStatusRepository
import com.da_emi_locker.backend.repository.SalesExecutiveRepository
import org.springframework.stereotype.Service

@Service
class DashboardService(
    private val customerRepository: CustomerRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val dealerRepository: DealerRepository,
    private val salesExecutiveRepository: SalesExecutiveRepository
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
    
    /**
     * Returns dashboard statistics.
     *
     * When [salesExecutiveId] is null, aggregates at dealer level (existing behaviour).
     * When [salesExecutiveId] is provided, aggregates only the customers that belong to
     * that sales executive and uses the sales executive's assigned kits as customerLimit.
     */
    fun getDashboardStats(
        dealerId: String,
        salesExecutiveId: String? = null
    ): DashboardStatsResponse {

        // --- Sales Executive-scoped stats ---
        if (salesExecutiveId != null) {
            val salesExecutive = salesExecutiveRepository.findBySalesExecutiveId(salesExecutiveId).orElse(null)
                ?: return DashboardStatsResponse(
                    success = false,
                    message = "Sales executive not found"
                )

            // Extra safety: ensure SE really belongs to this dealer
            if (salesExecutive.dealerId != dealerId) {
                return DashboardStatsResponse(
                    success = false,
                    message = "Access denied"
                )
            }

            val customerLimit = salesExecutive.assignedKits

            // Customers created by this SE
            val seCustomers = customerRepository.findBySalesExecutiveId(salesExecutiveId)
            val totalCustomers = seCustomers.size.toLong()

            val activeCustomers = seCustomers.count {
                it.status == CustomerStatus.active || it.status == CustomerStatus.installed
            }.toLong()
            val uninstalledCustomers = seCustomers.count {
                it.status == CustomerStatus.uninstalled
            }.toLong()
            val pendingActivation = seCustomers.count {
                it.status == CustomerStatus.pending_activation
            }.toLong()

            val kitRemaining = (customerLimit - totalCustomers.toInt()).coerceAtLeast(0)
            val installed = activeCustomers
            val uninstalled = uninstalledCustomers

            // Device statistics for this SE's customers only
            val customerIds = seCustomers.map { it.customerId }
            val allDevices = if (customerIds.isNotEmpty()) {
                deviceStatusRepository.findByCustomerIdIn(customerIds)
            } else {
                emptyList()
            }

            val totalDevices = allDevices.size.toLong()
            val onlineDevices = allDevices.count { it.status == DeviceStatusEnum.online }.toLong()
            val offlineDevices = allDevices.count { it.status == DeviceStatusEnum.offline }.toLong()
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

        // --- Dealer-scoped stats (existing behaviour) ---
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

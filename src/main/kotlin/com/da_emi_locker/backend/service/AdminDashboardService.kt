package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.CommandStatus
import com.da_emi_locker.backend.entity.CustomerStatus
import com.da_emi_locker.backend.entity.DealerStatus
import com.da_emi_locker.backend.entity.TicketStatus
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DealerRepository
import com.da_emi_locker.backend.repository.DeviceCommandRepository
import com.da_emi_locker.backend.repository.ActivityRepository
import com.da_emi_locker.backend.repository.DeviceStatusRepository
import com.da_emi_locker.backend.repository.SupportTicketRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class AdminDashboardService(
    private val dealerRepository: DealerRepository,
    private val customerRepository: CustomerRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val supportTicketRepository: SupportTicketRepository,
    private val deviceCommandRepository: DeviceCommandRepository,
    private val activityRepository: ActivityRepository
) {
    
    data class AdminDashboardResponse(
        val success: Boolean,
        val message: String,
        val stats: AdminDashboardStats? = null
    )
    
    data class AdminDashboardStats(
        val totalDealers: Long,
        val activeDealers: Long,
        val inactiveDealers: Long,
        val suspendedDealers: Long,
        val totalCustomers: Long,
        val totalKitsAssigned: Int,
        val totalKitsUsed: Long,
        val totalKitsAvailable: Int,
        val pendingActions: Int,
        val dealerDistribution: List<DealerDistributionData>
    )
    
    data class DealerDistributionData(
        val dealerId: String,
        val dealerName: String,
        val dealerStatus: String,
        val kitsAssigned: Int,
        val totalCustomers: Long,
        val activatedCustomers: Long,
        val pendingActivation: Long,
        val uninstalledCustomers: Long
    )
    
    fun getAdminDashboard(): AdminDashboardResponse {
        // Get all dealers
        val allDealers = dealerRepository.findAll()
        
        val totalDealers = allDealers.size.toLong()
        val activeDealers = allDealers.count { it.status == DealerStatus.active }.toLong()
        val inactiveDealers = allDealers.count { it.status == DealerStatus.inactive }.toLong()
        val suspendedDealers = allDealers.count { it.status == DealerStatus.suspended }.toLong()
        
        // Calculate total customers and kits
        var totalKitsAssigned = 0
        var totalKitsUsed = 0L
        val dealerDistributionList = mutableListOf<DealerDistributionData>()
        
        for (dealer in allDealers) {
            val dealerCustomers = customerRepository.findByDealerId(dealer.dealerId)
            val totalCustomers = dealerCustomers.size.toLong()
            val activatedCustomers = dealerCustomers.count { it.status == CustomerStatus.active }.toLong()
            
            // Get devices for this dealer's customers
            val customerIds = dealerCustomers.map { it.customerId }
            val devices = if (customerIds.isNotEmpty()) {
                deviceStatusRepository.findByCustomerIdIn(customerIds)
            } else {
                emptyList()
            }
            
            val installed = devices.size.toLong()
            val uninstalled = totalCustomers - installed
            val pendingActivation = uninstalled
            
            totalKitsAssigned += dealer.customerLimit
            totalKitsUsed += totalCustomers
            
            dealerDistributionList.add(
                DealerDistributionData(
                    dealerId = dealer.dealerId,
                    dealerName = dealer.name,
                    dealerStatus = dealer.status.name,
                    kitsAssigned = dealer.customerLimit,
                    totalCustomers = totalCustomers,
                    activatedCustomers = activatedCustomers,
                    pendingActivation = pendingActivation,
                    uninstalledCustomers = uninstalled
                )
            )
        }
        
        val totalKitsAvailable = totalKitsAssigned - totalKitsUsed.toInt()
        val totalCustomers = customerRepository.count()
        
        // Pending actions: open/in-progress/waiting support tickets + failed FCM commands + suspended dealers
        val openTicketsCount = supportTicketRepository.countByStatus(TicketStatus.open) +
            supportTicketRepository.countByStatus(TicketStatus.in_progress) +
            supportTicketRepository.countByStatus(TicketStatus.waiting_for_dealer)
        val failedCommandsCount = deviceCommandRepository.countByStatus(CommandStatus.failed)
        val pendingActions = (openTicketsCount + failedCommandsCount + suspendedDealers).toInt()
        
        return AdminDashboardResponse(
            success = true,
            message = "Admin dashboard statistics retrieved successfully",
            stats = AdminDashboardStats(
                totalDealers = totalDealers,
                activeDealers = activeDealers,
                inactiveDealers = inactiveDealers,
                suspendedDealers = suspendedDealers,
                totalCustomers = totalCustomers,
                totalKitsAssigned = totalKitsAssigned,
                totalKitsUsed = totalKitsUsed,
                totalKitsAvailable = if (totalKitsAvailable >= 0) totalKitsAvailable else 0,
                pendingActions = pendingActions,
                dealerDistribution = dealerDistributionList
            )
        )
    }
    
    /** Pending actions list for admin: open tickets, failed commands, suspended dealers */
    data class PendingActionsResponse(
        val success: Boolean,
        val message: String,
        val openTickets: List<PendingTicketItem> = emptyList(),
        val failedCommands: List<PendingCommandItem> = emptyList(),
        val suspendedDealers: List<PendingDealerItem> = emptyList()
    )
    
    data class PendingTicketItem(
        val ticketId: String,
        val subject: String,
        val dealerId: String,
        val status: String,
        val priority: String,
        val createdAt: String
    )
    
    data class PendingCommandItem(
        val id: Long,
        val commandType: String,
        val customerId: String?,
        val deviceId: String,
        val errorMessage: String?,
        val createdAt: String
    )
    
    data class PendingDealerItem(
        val dealerId: String,
        val name: String,
        val email: String?
    )
    
    fun getPendingActions(): PendingActionsResponse {
        val openStatuses = listOf(TicketStatus.open, TicketStatus.in_progress, TicketStatus.waiting_for_dealer)
        val openTickets = openStatuses.flatMap { supportTicketRepository.findAllWithFilters(it, null, null, null, PageRequest.of(0, 50)).content }
            .map { t ->
                PendingTicketItem(
                    ticketId = t.ticketId,
                    subject = t.subject,
                    dealerId = t.dealerId,
                    status = t.status.name,
                    priority = t.priority.name,
                    createdAt = t.createdAt.toString()
                )
            }
        
        val failedCommandsPage = deviceCommandRepository.findByStatusOrderByUpdatedAtDesc(CommandStatus.failed, PageRequest.of(0, 50))
        val failedCommands = failedCommandsPage.content.map { c ->
                PendingCommandItem(
                    id = c.id ?: 0L,
                    commandType = c.commandType,
                    customerId = c.customerId,
                    deviceId = c.deviceId,
                    errorMessage = c.errorMessage,
                    createdAt = c.updatedAt.toString()
                )
            }
        
        val suspendedDealers = dealerRepository.findAll().filter { it.status == DealerStatus.suspended }
            .map { d ->
                PendingDealerItem(
                    dealerId = d.dealerId,
                    name = d.name,
                    email = d.email
                )
            }
        
        return PendingActionsResponse(
            success = true,
            message = "Pending actions retrieved",
            openTickets = openTickets,
            failedCommands = failedCommands,
            suspendedDealers = suspendedDealers
        )
    }
    
    /** Reports: dashboard stats + activity counts by type for analytics */
    data class ReportsResponse(
        val success: Boolean,
        val message: String,
        val stats: AdminDashboardStats? = null,
        val activityCountByType: List<ActivityCountByType> = emptyList()
    )
    
    data class ActivityCountByType(
        val activityType: String,
        val count: Long
    )
    
    fun getReports(): ReportsResponse {
        val dashboard = getAdminDashboard()
        val types = activityRepository.findDistinctActivityTypes()
        val activityCountByType = types.map { type ->
            val count = activityRepository.findByActivityType(type).size.toLong()
            ActivityCountByType(activityType = type, count = count)
        }.sortedByDescending { it.count }
        return ReportsResponse(
            success = true,
            message = "Reports retrieved",
            stats = dashboard.stats,
            activityCountByType = activityCountByType
        )
    }
}

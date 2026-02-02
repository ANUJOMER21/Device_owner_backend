package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.CommandStatus
import com.da_emi_locker.backend.entity.DeviceCommand
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DeviceCommandRepository
import com.da_emi_locker.backend.repository.DeviceStatusRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class CommandHistoryService(
    private val deviceCommandRepository: DeviceCommandRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val customerRepository: CustomerRepository
) {
    
    data class CommandHistoryResponse(
        val success: Boolean,
        val message: String,
        val commands: List<CommandHistoryData>? = null,
        val total: Long? = null,
        val page: Int? = null,
        val pageSize: Int? = null,
        val totalPages: Int? = null
    )
    
    data class CommandHistoryData(
        val commandId: Long,
        val deviceId: String,
        val customerId: String,
        val customerName: String,
        val commandType: String,
        val commandData: String?,
        val status: String,
        val executedAt: String?,
        val errorMessage: String?,
        val retryCount: Int,
        val createdAt: String,
        val updatedAt: String
    )
    
    fun getCommandHistory(
        dealerId: String,
        customerId: String? = null,
        commandType: String? = null,
        status: String? = null,
        page: Int = 0,
        size: Int = 20
    ): CommandHistoryResponse {
        // Get all customer IDs for this dealer
        val dealerCustomers = customerRepository.findByDealerId(dealerId)
        val customerIds = dealerCustomers.map { it.customerId }.toSet()
        
        if (customerIds.isEmpty()) {
            return CommandHistoryResponse(
                success = true,
                message = "No customers found for dealer",
                commands = emptyList(),
                total = 0,
                page = page,
                pageSize = size,
                totalPages = 0
            )
        }
        
        // Filter by customer if specified
        val targetCustomerIds = if (customerId != null) {
            if (!customerIds.contains(customerId)) {
                return CommandHistoryResponse(
                    success = false,
                    message = "Access denied or customer not found"
                )
            }
            listOf(customerId)
        } else {
            customerIds.toList()
        }
        
        // Get commands directly by customerId (more efficient)
        val allCommands = targetCustomerIds.flatMap { cid ->
            val customerCommands = deviceCommandRepository.findByCustomerId(cid)
            customerCommands.filter { command ->
                (commandType == null || command.commandType == commandType) &&
                (status == null || command.status.name.equals(status, ignoreCase = true))
            }
        }.sortedByDescending { it.createdAt }
        
        // Apply pagination
        val total = allCommands.size.toLong()
        val start = page * size
        val end = minOf(start + size, allCommands.size)
        val paginatedCommands = if (start < allCommands.size) {
            allCommands.subList(start, end)
        } else {
            emptyList()
        }
        
        // Create customer name map
        val customerMap = dealerCustomers.associateBy { it.customerId }
        
        val commandDataList = paginatedCommands.map { command ->
            val customer = command.customerId?.let { customerMap[it] }
            
            CommandHistoryData(
                commandId = command.id ?: 0,
                deviceId = command.deviceId,
                customerId = command.customerId ?: "",
                customerName = customer?.name ?: "",
                commandType = command.commandType,
                commandData = command.commandData,
                status = command.status.name,
                executedAt = command.executedAt?.toString(),
                errorMessage = command.errorMessage,
                retryCount = command.retryCount,
                createdAt = command.createdAt?.toString() ?: "",
                updatedAt = command.updatedAt?.toString() ?: ""
            )
        }
        
        return CommandHistoryResponse(
            success = true,
            message = "Command history retrieved successfully",
            commands = commandDataList,
            total = total,
            page = page,
            pageSize = size,
            totalPages = if (total > 0) ((total + size - 1) / size).toInt() else 0
        )
    }
    
    fun getCommandHistoryByCustomer(
        dealerId: String,
        customerId: String,
        page: Int = 0,
        size: Int = 20
    ): CommandHistoryResponse {
        return getCommandHistory(dealerId, customerId, null, null, page, size)
    }
}

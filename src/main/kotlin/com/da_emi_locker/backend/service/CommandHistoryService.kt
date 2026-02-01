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
        
        // Get all device IDs for dealer's customers
        val allDevices = if (customerIds.isNotEmpty()) {
            deviceStatusRepository.findByCustomerIdIn(customerIds.toList())
        } else {
            emptyList()
        }
        val deviceIds = allDevices.map { it.deviceId }.toSet()
        
        // Filter by customer if specified
        val filteredDeviceIds = if (customerId != null) {
            if (!customerIds.contains(customerId)) {
                return CommandHistoryResponse(
                    success = false,
                    message = "Access denied or customer not found"
                )
            }
            allDevices.filter { it.customerId == customerId }.map { it.deviceId }.toSet()
        } else {
            deviceIds
        }
        
        // Get all commands for these devices
        val allCommands = deviceCommandRepository.findAll()
            .filter { command ->
                filteredDeviceIds.contains(command.deviceId) &&
                (commandType == null || command.commandType == commandType) &&
                (status == null || command.status.name == status)
            }
            .sortedByDescending { it.createdAt }
        
        // Apply pagination manually
        val start = page * size
        val end = minOf(start + size, allCommands.size)
        val paginatedCommands = allCommands.subList(start, end)
        
        // Create customer name map
        val customerMap = dealerCustomers.associateBy { it.customerId }
        val deviceToCustomerMap = allDevices.associate { it.deviceId to it.customerId }
        
        val commandDataList = paginatedCommands.map { command ->
            val customerIdForDevice = deviceToCustomerMap[command.deviceId]
            val customer = customerIdForDevice?.let { customerMap[it] }
            
            CommandHistoryData(
                commandId = command.id ?: 0,
                deviceId = command.deviceId,
                customerId = customerIdForDevice ?: "",
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
            total = allCommands.size.toLong(),
            page = page,
            pageSize = size,
            totalPages = (allCommands.size + size - 1) / size
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

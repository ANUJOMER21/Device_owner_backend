package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.*
import com.da_emi_locker.backend.repository.DealerRepository
import com.da_emi_locker.backend.repository.SupportTicketRepository
import com.da_emi_locker.backend.repository.TicketMessageRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.NoSuchElementException
import java.util.UUID

@Service
class SupportTicketService(
    private val supportTicketRepository: SupportTicketRepository,
    private val ticketMessageRepository: TicketMessageRepository,
    private val dealerRepository: DealerRepository
) {
    
    // Request/Response DTOs
    data class CreateTicketRequest(
        val dealerId: String,
        val subject: String,
        val description: String? = null,
        val category: String? = null,
        val priority: String = "medium",
        val attachmentUrl: String? = null,
        val attachmentFileName: String? = null
    )
    
    data class UpdateTicketRequest(
        val subject: String? = null,
        val description: String? = null,
        val category: String? = null,
        val priority: String? = null,
        val status: String? = null,
        val assignedTo: String? = null
    )
    
    data class AddMessageRequest(
        val message: String,
        val senderName: String? = null,
        val attachmentUrl: String? = null,
        val attachmentFileName: String? = null
    )
    
    data class TicketResponse(
        val success: Boolean,
        val message: String,
        val ticket: TicketData? = null
    )
    
    data class TicketListResponse(
        val success: Boolean,
        val message: String,
        val tickets: List<TicketData> = emptyList(),
        val total: Long = 0,
        val page: Int = 0,
        val pageSize: Int = 20,
        val totalPages: Int = 0
    )
    
    data class TicketData(
        val ticketId: String,
        val dealerId: String,
        val dealerName: String? = null,
        val subject: String,
        val description: String?,
        val category: String?,
        val priority: String,
        val status: String,
        val assignedTo: String?,
        val createdBy: String,
        val createdByType: String,
        val resolvedAt: String?,
        val createdAt: String,
        val updatedAt: String,
        val messages: List<MessageData> = emptyList(),
        val messageCount: Int = 0
    )
    
    data class MessageData(
        val id: Long,
        val ticketId: String,
        val senderId: String,
        val senderType: String,
        val senderName: String?,
        val message: String,
        val createdAt: String,
        val attachmentUrl: String? = null,
        val attachmentFileName: String? = null
    )
    
    data class TicketStatsResponse(
        val success: Boolean,
        val message: String,
        val stats: TicketStats? = null
    )
    
    data class TicketStats(
        val total: Long,
        val open: Long,
        val inProgress: Long,
        val waitingForDealer: Long,
        val resolved: Long,
        val closed: Long
    )
    
    /**
     * Create a new support ticket (admin creates for a dealer)
     */
    @Transactional
    fun createTicket(adminId: String, request: CreateTicketRequest): TicketResponse {
        return createTicketInternal(adminId, request, CreatorType.admin, SenderType.admin)
    }
    
    /**
     * Create a new support ticket (dealer creates for themselves)
     */
    @Transactional
    fun createTicketByDealer(dealerId: String, request: CreateTicketRequest): TicketResponse {
        return createTicketInternal(dealerId, request, CreatorType.dealer, SenderType.dealer)
    }
    
    /**
     * Internal method to create a ticket
     */
    @Transactional
    private fun createTicketInternal(
        creatorId: String,
        request: CreateTicketRequest,
        creatorType: CreatorType,
        senderType: SenderType
    ): TicketResponse {
        // Validate dealer exists
        val dealerOpt = dealerRepository.findByDealerId(request.dealerId)
        if (dealerOpt.isEmpty) {
            return TicketResponse(
                success = false,
                message = "Dealer not found with ID: ${request.dealerId}"
            )
        }
        
        if (request.subject.isBlank()) {
            return TicketResponse(
                success = false,
                message = "Subject is required"
            )
        }
        
        val ticketId = generateTicketId()
        
        val priority = enumValues<TicketPriority>().find { it.name.equals(request.priority, ignoreCase = true) }
            ?: TicketPriority.medium
        
        val ticket = SupportTicket().apply {
            this.ticketId = ticketId
            this.dealerId = request.dealerId
            this.subject = request.subject.trim()
            this.description = request.description?.trim()
            this.category = request.category?.trim()
            this.priority = priority
            this.status = TicketStatus.open
            this.createdBy = creatorId
            this.createdByType = creatorType
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }
        
        val savedTicket = supportTicketRepository.save(ticket)
        
        // If description provided, create initial message (with optional attachment)
        if (!request.description.isNullOrBlank()) {
            val initialMessage = TicketMessage().apply {
                this.ticketId = ticketId
                this.senderId = creatorId
                this.senderType = senderType
                this.senderName = if (senderType == SenderType.admin) "Admin" else "Dealer"
                this.message = request.description.trim()
                this.attachmentUrl = request.attachmentUrl
                this.attachmentFileName = request.attachmentFileName
                this.createdAt = Instant.now()
                this.updatedAt = Instant.now()
            }
            ticketMessageRepository.save(initialMessage)
        }
        
        val dealer = dealerOpt.orElseThrow { NoSuchElementException("Dealer not found") }
        val messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
        
        return TicketResponse(
            success = true,
            message = "Support ticket created successfully",
            ticket = mapToTicketData(savedTicket, dealer.name, messages)
        )
    }
    
    /**
     * Get all tickets with optional filters
     */
    fun getAllTickets(
        status: String? = null,
        priority: String? = null,
        dealerId: String? = null,
        search: String? = null,
        page: Int = 0,
        pageSize: Int = 20
    ): TicketListResponse {
        val pageable = PageRequest.of(page, pageSize, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
        
        val statusEnum = status?.let { s ->
            enumValues<TicketStatus>().find { it.name.equals(s, ignoreCase = true) }
        }
        val priorityEnum = priority?.let { p ->
            enumValues<TicketPriority>().find { it.name.equals(p, ignoreCase = true) }
        }
        
        // Use simple findAll if no filters, or use filtered query
        val hasFilters = statusEnum != null || priorityEnum != null || !dealerId.isNullOrBlank() || !search.isNullOrBlank()
        
        val ticketsPage = if (hasFilters) {
            supportTicketRepository.findAllWithFilters(
                statusEnum, priorityEnum, dealerId?.takeIf { it.isNotBlank() }, search?.takeIf { it.isNotBlank() }, pageable
            )
        } else {
            supportTicketRepository.findAll(pageable)
        }
        
        val ticketDataList = ticketsPage.content.map { ticket ->
            val dealer = dealerRepository.findByDealerId(ticket.dealerId).orElse(null)
            val messageCount = ticketMessageRepository.countByTicketId(ticket.ticketId).toInt()
            mapToTicketData(ticket, dealer?.name, emptyList(), messageCount)
        }
        
        return TicketListResponse(
            success = true,
            message = "Tickets retrieved successfully",
            tickets = ticketDataList,
            total = ticketsPage.totalElements,
            page = ticketsPage.number,
            pageSize = ticketsPage.size,
            totalPages = ticketsPage.totalPages
        )
    }
    
    /**
     * Get a single ticket by ID with all messages
     */
    fun getTicket(ticketId: String): TicketResponse {
        val ticketOpt = supportTicketRepository.findByTicketId(ticketId)
        if (ticketOpt.isEmpty) {
            return TicketResponse(
                success = false,
                message = "Ticket not found"
            )
        }
        
        val ticket = ticketOpt.orElseThrow { NoSuchElementException("Ticket not found") }
        val dealer = dealerRepository.findByDealerId(ticket.dealerId).orElse(null)
        val messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
        
        return TicketResponse(
            success = true,
            message = "Ticket retrieved successfully",
            ticket = mapToTicketData(ticket, dealer?.name, messages)
        )
    }
    
    /**
     * Update ticket details
     */
    @Transactional
    fun updateTicket(ticketId: String, request: UpdateTicketRequest): TicketResponse {
        val ticketOpt = supportTicketRepository.findByTicketId(ticketId)
        if (ticketOpt.isEmpty) {
            return TicketResponse(
                success = false,
                message = "Ticket not found"
            )
        }
        
        val ticket = ticketOpt.orElseThrow { NoSuchElementException("Ticket not found") }
        
        request.subject?.let { ticket.subject = it.trim() }
        request.description?.let { ticket.description = it.trim() }
        request.category?.let { ticket.category = it.trim() }
        request.assignedTo?.let { ticket.assignedTo = it.trim() }
        
        request.priority?.let { p ->
            val newPriority = enumValues<TicketPriority>().find { it.name.equals(p, ignoreCase = true) }
            if (newPriority == null) {
                return TicketResponse(
                    success = false,
                    message = "Invalid priority. Must be: low, medium, high, or urgent"
                )
            }
            ticket.priority = newPriority
        }
        
        request.status?.let { s ->
            val newStatus = enumValues<TicketStatus>().find { it.name.equals(s, ignoreCase = true) }
            if (newStatus == null) {
                return TicketResponse(
                    success = false,
                    message = "Invalid status. Must be: open, in_progress, waiting_for_dealer, resolved, or closed"
                )
            }
            ticket.status = newStatus
            if (newStatus == TicketStatus.resolved || newStatus == TicketStatus.closed) {
                ticket.resolvedAt = Instant.now()
            }
        }
        
        ticket.updatedAt = Instant.now()
        val savedTicket = supportTicketRepository.save(ticket)
        
        val dealer = dealerRepository.findByDealerId(ticket.dealerId).orElse(null)
        val messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
        
        return TicketResponse(
            success = true,
            message = "Ticket updated successfully",
            ticket = mapToTicketData(savedTicket, dealer?.name, messages)
        )
    }
    
    /**
     * Add a message to a ticket
     */
    @Transactional
    fun addMessage(ticketId: String, senderId: String, senderType: SenderType, request: AddMessageRequest): TicketResponse {
        val ticketOpt = supportTicketRepository.findByTicketId(ticketId)
        if (ticketOpt.isEmpty) {
            return TicketResponse(
                success = false,
                message = "Ticket not found"
            )
        }
        
        if (request.message.isBlank()) {
            return TicketResponse(
                success = false,
                message = "Message cannot be empty"
            )
        }
        
        val ticket = ticketOpt.orElseThrow { NoSuchElementException("Ticket not found") }
        
        if (ticket.status == TicketStatus.closed) {
            return TicketResponse(
                success = false,
                message = "Cannot reply to a closed ticket"
            )
        }
        
        // Create message
        val ticketMessage = TicketMessage().apply {
            this.ticketId = ticketId
            this.senderId = senderId
            this.senderType = senderType
            this.senderName = request.senderName ?: if (senderType == SenderType.admin) "Admin" else "Dealer"
            this.message = request.message.trim()
            this.attachmentUrl = request.attachmentUrl
            this.attachmentFileName = request.attachmentFileName
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }
        ticketMessageRepository.save(ticketMessage)
        
        // Update ticket status based on who sent the message
        if (senderType == SenderType.admin) {
            if (ticket.status == TicketStatus.open) {
                ticket.status = TicketStatus.in_progress
            }
            // When admin replies, set to waiting_for_dealer
            if (ticket.status != TicketStatus.resolved && ticket.status != TicketStatus.closed) {
                ticket.status = TicketStatus.waiting_for_dealer
            }
        } else {
            // When dealer replies, set to in_progress
            if (ticket.status == TicketStatus.waiting_for_dealer) {
                ticket.status = TicketStatus.in_progress
            }
        }
        
        ticket.updatedAt = Instant.now()
        supportTicketRepository.save(ticket)
        
        val dealer = dealerRepository.findByDealerId(ticket.dealerId).orElse(null)
        val messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
        
        return TicketResponse(
            success = true,
            message = "Message added successfully",
            ticket = mapToTicketData(ticket, dealer?.name, messages)
        )
    }
    
    /**
     * Delete a ticket
     */
    @Transactional
    fun deleteTicket(ticketId: String): TicketResponse {
        val ticketOpt = supportTicketRepository.findByTicketId(ticketId)
        if (ticketOpt.isEmpty) {
            return TicketResponse(
                success = false,
                message = "Ticket not found"
            )
        }
        
        val ticket = ticketOpt.orElseThrow { NoSuchElementException("Ticket not found") }
        
        // Delete messages first
        ticketMessageRepository.deleteByTicketId(ticketId)
        
        // Delete ticket
        supportTicketRepository.delete(ticket)
        
        return TicketResponse(
            success = true,
            message = "Ticket deleted successfully"
        )
    }
    
    /**
     * Get ticket statistics
     */
    fun getTicketStats(): TicketStatsResponse {
        val stats = TicketStats(
            total = supportTicketRepository.count(),
            open = supportTicketRepository.countByStatus(TicketStatus.open),
            inProgress = supportTicketRepository.countByStatus(TicketStatus.in_progress),
            waitingForDealer = supportTicketRepository.countByStatus(TicketStatus.waiting_for_dealer),
            resolved = supportTicketRepository.countByStatus(TicketStatus.resolved),
            closed = supportTicketRepository.countByStatus(TicketStatus.closed)
        )
        
        return TicketStatsResponse(
            success = true,
            message = "Ticket statistics retrieved successfully",
            stats = stats
        )
    }
    
    private fun mapToTicketData(
        ticket: SupportTicket, 
        dealerName: String?,
        messages: List<TicketMessage>,
        messageCount: Int? = null
    ): TicketData {
        return TicketData(
            ticketId = ticket.ticketId,
            dealerId = ticket.dealerId,
            dealerName = dealerName,
            subject = ticket.subject,
            description = ticket.description,
            category = ticket.category,
            priority = ticket.priority.name,
            status = ticket.status.name,
            assignedTo = ticket.assignedTo,
            createdBy = ticket.createdBy,
            createdByType = ticket.createdByType.name,
            resolvedAt = ticket.resolvedAt?.toString(),
            createdAt = ticket.createdAt?.toString() ?: "",
            updatedAt = ticket.updatedAt?.toString() ?: "",
            messages = messages.map { msg ->
                MessageData(
                    id = msg.id ?: 0,
                    ticketId = msg.ticketId,
                    senderId = msg.senderId,
                    senderType = msg.senderType.name,
                    senderName = msg.senderName,
                    message = msg.message,
                    createdAt = msg.createdAt?.toString() ?: "",
                    attachmentUrl = msg.attachmentUrl,
                    attachmentFileName = msg.attachmentFileName
                )
            },
            messageCount = messageCount ?: messages.size
        )
    }
    
    private fun generateTicketId(): String {
        val timestamp = System.currentTimeMillis().toString().takeLast(8)
        val random = UUID.randomUUID().toString().substring(0, 4).uppercase().replace("-", "")
        return "TKT$timestamp$random"
    }
}

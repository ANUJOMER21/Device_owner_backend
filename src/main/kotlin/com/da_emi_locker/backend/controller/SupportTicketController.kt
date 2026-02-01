package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.entity.SenderType
import com.da_emi_locker.backend.service.S3StorageService
import com.da_emi_locker.backend.service.SupportTicketService
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/support")
class SupportTicketController(
    private val supportTicketService: SupportTicketService,
    private val s3StorageService: S3StorageService
) {
    
    // ApiResponse wrapper for dealer app compatibility
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )
    
    // TicketDto matching dealer app format
    data class TicketDto(
        val ticketId: String,
        val subject: String,
        val category: String,
        val description: String,
        val status: String,
        val priority: String,
        val createdDate: String,
        val lastUpdated: String,
        val responses: List<TicketMessageDto>
    )
    
    data class TicketMessageDto(
        val messageId: String,
        val message: String,
        @param:JsonProperty("isFromSupport") val isFromSupport: Boolean,
        val timestamp: String,
        val attachmentUrl: String? = null,
        val attachmentFileName: String? = null
    )
    
    data class CreateTicketRequestDto(
        @field:NotBlank(message = "Subject is required")
        val subject: String,
        val category: String? = null,
        val priority: String = "medium",
        @field:NotBlank(message = "Description is required")
        val description: String
    )
    
    data class AddTicketReplyRequestDto(
        @field:NotBlank(message = "Ticket ID is required")
        val ticketId: String,
        @field:NotBlank(message = "Message is required")
        val message: String
    )
    
    @GetMapping("/tickets")
    fun getTickets(
        @RequestAttribute("dealerId") dealerId: String?
    ): ResponseEntity<ApiResponse<List<TicketDto>>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = supportTicketService.getAllTickets(null, null, dealerId, null, 0, 100)
        
        return if (response.success) {
            val ticketDtos = response.tickets.map { ticket ->
                mapToTicketDto(ticket)
            }
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = ticketDtos
                )
            )
        } else {
            ResponseEntity.status(400).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
    }
    
    @GetMapping("/tickets/{ticketId}")
    fun getTicketById(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable ticketId: String
    ): ResponseEntity<ApiResponse<TicketDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = supportTicketService.getTicket(ticketId)
        
        if (!response.success || response.ticket == null) {
            return ResponseEntity.status(404).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
        
        // Verify ticket belongs to dealer
        if (response.ticket.dealerId != dealerId) {
            return ResponseEntity.status(403).body(
                ApiResponse(
                    success = false,
                    message = "Access denied"
                )
            )
        }
        
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = response.message,
                data = mapToTicketDto(response.ticket)
            )
        )
    }
    
    @PostMapping(value = ["/tickets"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun createTicket(
        @RequestAttribute("dealerId") dealerId: String?,
        @Valid @RequestBody request: CreateTicketRequestDto
    ): ResponseEntity<ApiResponse<TicketDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = supportTicketService.createTicketByDealer(
            dealerId,
            SupportTicketService.CreateTicketRequest(
                dealerId = dealerId,
                subject = request.subject,
                description = request.description,
                category = request.category,
                priority = request.priority
            )
        )
        
        return if (response.success && response.ticket != null) {
            ResponseEntity.status(201).body(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = mapToTicketDto(response.ticket)
                )
            )
        } else {
            ResponseEntity.status(400).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
    }
    
    @PostMapping(value = ["/tickets"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createTicketWithAttachment(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestParam("subject") subject: String,
        @RequestParam("category") category: String?,
        @RequestParam("priority") priority: String?,
        @RequestParam("description") description: String,
        @RequestParam(value = "attachment", required = false) attachment: MultipartFile?
    ): ResponseEntity<ApiResponse<TicketDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized")
            )
        }
        if (subject.isBlank()) {
            return ResponseEntity.status(400).body(
                ApiResponse(success = false, message = "Subject is required")
            )
        }
        if (description.isBlank()) {
            return ResponseEntity.status(400).body(
                ApiResponse(success = false, message = "Description is required")
            )
        }
        var attachmentUrl: String? = null
        var attachmentFileName: String? = null
        attachment?.let { file ->
            if (!file.isEmpty) {
                val maxSize = 10 * 1024 * 1024L
                if (file.size > maxSize) {
                    return ResponseEntity.status(400).body(
                        ApiResponse(success = false, message = "Attachment must be under 10MB")
                    )
                }
                val contentType = file.contentType?.lowercase() ?: ""
                val allowed = contentType.startsWith("image/") || contentType == "application/pdf"
                if (!allowed) {
                    return ResponseEntity.status(400).body(
                        ApiResponse(success = false, message = "Attachment must be image or PDF")
                    )
                }
                try {
                    attachmentUrl = s3StorageService.uploadImage(file, "ticket_attachments")
                        ?: throw IllegalStateException("S3 storage is not configured")
                    attachmentFileName = file.originalFilename?.take(255)
                } catch (e: Exception) {
                    return ResponseEntity.status(500).body(
                        ApiResponse(success = false, message = "Error uploading attachment: ${e.message}")
                    )
                }
            }
        }
        val response = supportTicketService.createTicketByDealer(
            dealerId,
            SupportTicketService.CreateTicketRequest(
                dealerId = dealerId,
                subject = subject.trim(),
                description = description.trim(),
                category = category?.trim(),
                priority = priority?.trim() ?: "medium",
                attachmentUrl = attachmentUrl,
                attachmentFileName = attachmentFileName
            )
        )
        return if (response.success && response.ticket != null) {
            ResponseEntity.status(201).body(
                ApiResponse(success = true, message = response.message, data = mapToTicketDto(response.ticket))
            )
        } else {
            ResponseEntity.status(400).body(
                ApiResponse(success = false, message = response.message)
            )
        }
    }
    
    @PostMapping(value = ["/tickets/reply"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun addTicketReply(
        @RequestAttribute("dealerId") dealerId: String?,
        @Valid @RequestBody request: AddTicketReplyRequestDto
    ): ResponseEntity<ApiResponse<TicketMessageDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        // Verify ticket belongs to dealer
        val ticketResponse = supportTicketService.getTicket(request.ticketId)
        if (!ticketResponse.success || ticketResponse.ticket == null) {
            return ResponseEntity.status(404).body(
                ApiResponse(
                    success = false,
                    message = "Ticket not found"
                )
            )
        }
        
        if (ticketResponse.ticket.dealerId != dealerId) {
            return ResponseEntity.status(403).body(
                ApiResponse(
                    success = false,
                    message = "Access denied"
                )
            )
        }
        if (ticketResponse.ticket.status.equals("closed", ignoreCase = true)) {
            return ResponseEntity.status(400).body(
                ApiResponse(
                    success = false,
                    message = "Cannot reply to a closed ticket"
                )
            )
        }
        
        val response = supportTicketService.addMessage(
            request.ticketId,
            dealerId,
            SenderType.dealer,
            SupportTicketService.AddMessageRequest(
                message = request.message,
                senderName = null // Will use default "Dealer"
            )
        )
        
        if (!response.success || response.ticket == null) {
            return ResponseEntity.status(400).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
        
        // Return the last message (which is the one we just added)
        val lastMessage = response.ticket.messages.lastOrNull()
        if (lastMessage != null) {
            return ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = TicketMessageDto(
                        messageId = lastMessage.id.toString(),
                        message = lastMessage.message,
                        isFromSupport = lastMessage.senderType.equals("admin", ignoreCase = true),
                        timestamp = lastMessage.createdAt
                    )
                )
            )
        }
        
        return ResponseEntity.status(500).body(
            ApiResponse(
                success = false,
                message = "Failed to retrieve message"
            )
        )
    }
    
    /** Reply with optional attachment (image or PDF, max 10MB). Use multipart/form-data: ticketId (required), message (required), attachment (optional). */
    @PostMapping(value = ["/tickets/reply"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addTicketReplyWithAttachment(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestParam("ticketId") ticketId: String,
        @RequestParam("message") message: String,
        @RequestParam(value = "attachment", required = false) attachment: MultipartFile?
    ): ResponseEntity<ApiResponse<TicketMessageDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized")
            )
        }
        if (message.isBlank()) {
            return ResponseEntity.status(400).body(
                ApiResponse(success = false, message = "Message is required")
            )
        }
        val ticketResponse = supportTicketService.getTicket(ticketId)
        if (!ticketResponse.success || ticketResponse.ticket == null) {
            return ResponseEntity.status(404).body(
                ApiResponse(success = false, message = "Ticket not found")
            )
        }
        if (ticketResponse.ticket!!.dealerId != dealerId) {
            return ResponseEntity.status(403).body(
                ApiResponse(success = false, message = "Access denied")
            )
        }
        if (ticketResponse.ticket!!.status.equals("closed", ignoreCase = true)) {
            return ResponseEntity.status(400).body(
                ApiResponse(success = false, message = "Cannot reply to a closed ticket")
            )
        }
        var attachmentUrl: String? = null
        var attachmentFileName: String? = null
        attachment?.let { file ->
            if (!file.isEmpty) {
                val maxSize = 10 * 1024 * 1024L // 10MB
                if (file.size > maxSize) {
                    return ResponseEntity.status(400).body(
                        ApiResponse(success = false, message = "Attachment must be under 10MB")
                    )
                }
                val contentType = file.contentType?.lowercase() ?: ""
                val allowed = contentType.startsWith("image/") ||
                    contentType == "application/pdf"
                if (!allowed) {
                    return ResponseEntity.status(400).body(
                        ApiResponse(success = false, message = "Attachment must be image or PDF")
                    )
                }
                try {
                    attachmentUrl = s3StorageService.uploadImage(file, "ticket_attachments")
                        ?: throw IllegalStateException("S3 storage is not configured")
                    attachmentFileName = file.originalFilename?.take(255)
                } catch (e: Exception) {
                    return ResponseEntity.status(500).body(
                        ApiResponse(success = false, message = "Error uploading attachment: ${e.message}")
                    )
                }
            }
        }
        val response = supportTicketService.addMessage(
            ticketId,
            dealerId,
            SenderType.dealer,
            SupportTicketService.AddMessageRequest(
                message = message.trim(),
                senderName = null,
                attachmentUrl = attachmentUrl,
                attachmentFileName = attachmentFileName
            )
        )
        if (!response.success || response.ticket == null) {
            return ResponseEntity.status(400).body(
                ApiResponse(success = false, message = response.message)
            )
        }
        val lastMessage = response.ticket!!.messages.lastOrNull()
        return if (lastMessage != null) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = TicketMessageDto(
                        messageId = lastMessage.id.toString(),
                        message = lastMessage.message,
                        isFromSupport = lastMessage.senderType.equals("admin", ignoreCase = true),
                        timestamp = lastMessage.createdAt,
                        attachmentUrl = lastMessage.attachmentUrl,
                        attachmentFileName = lastMessage.attachmentFileName
                    )
                )
            )
        } else {
            ResponseEntity.status(500).body(
                ApiResponse(success = false, message = "Failed to retrieve message")
            )
        }
    }
    
    @PutMapping("/tickets/{ticketId}/close")
    fun closeTicket(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable ticketId: String
    ): ResponseEntity<ApiResponse<Boolean>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        // Verify ticket belongs to dealer
        val ticketResponse = supportTicketService.getTicket(ticketId)
        if (!ticketResponse.success || ticketResponse.ticket == null) {
            return ResponseEntity.status(404).body(
                ApiResponse(
                    success = false,
                    message = "Ticket not found"
                )
            )
        }
        
        if (ticketResponse.ticket.dealerId != dealerId) {
            return ResponseEntity.status(403).body(
                ApiResponse(
                    success = false,
                    message = "Access denied"
                )
            )
        }
        
        val response = supportTicketService.updateTicket(
            ticketId,
            SupportTicketService.UpdateTicketRequest(
                status = "closed"
            )
        )
        
        return if (response.success) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = true
                )
            )
        } else {
            ResponseEntity.status(400).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
    }
    
    private fun mapToTicketDto(ticket: SupportTicketService.TicketData): TicketDto {
        return TicketDto(
            ticketId = ticket.ticketId,
            subject = ticket.subject,
            category = ticket.category ?: "",
            description = ticket.description ?: "",
            status = ticket.status,
            priority = ticket.priority,
            createdDate = ticket.createdAt,
            lastUpdated = ticket.updatedAt,
            responses = ticket.messages.map { message ->
                TicketMessageDto(
                    messageId = message.id.toString(),
                    message = message.message,
                    isFromSupport = message.senderType.equals("admin", ignoreCase = true),
                    timestamp = message.createdAt,
                    attachmentUrl = message.attachmentUrl,
                    attachmentFileName = message.attachmentFileName
                )
            }
        )
    }
}

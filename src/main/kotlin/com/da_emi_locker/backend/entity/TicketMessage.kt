package com.da_emi_locker.backend.entity

import jakarta.persistence.*

/**
 * Ticket message entity for support ticket conversations
 */
@Entity
@Table(name = "ticket_messages")
class TicketMessage : BaseEntity() {
    
    @Column(name = "ticket_id", nullable = false, length = 50)
    var ticketId: String = ""
    
    @Column(name = "sender_id", nullable = false, length = 100)
    var senderId: String = ""
    
    @Column(name = "sender_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var senderType: SenderType = SenderType.admin
    
    @Column(name = "sender_name", length = 255)
    var senderName: String? = null
    
    @Column(nullable = false, columnDefinition = "TEXT")
    var message: String = ""
    
    @Column(name = "attachment_url", length = 500)
    var attachmentUrl: String? = null
    
    @Column(name = "attachment_file_name", length = 255)
    var attachmentFileName: String? = null
}

enum class SenderType {
    admin,
    dealer
}

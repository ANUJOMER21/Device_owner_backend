package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Support ticket entity for dealer support requests
 * Managed by admin panel
 */
@Entity
@Table(name = "support_tickets")
class SupportTicket : BaseEntity() {
    
    @Column(name = "ticket_id", unique = true, nullable = false, length = 50)
    var ticketId: String = ""
    
    @Column(name = "dealer_id", nullable = false, length = 50)
    var dealerId: String = ""
    
    @Column(nullable = false, length = 255)
    var subject: String = ""
    
    @Column(columnDefinition = "TEXT")
    var description: String? = null
    
    @Column(length = 50)
    var category: String? = null
    
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    var priority: TicketPriority = TicketPriority.medium
    
    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    var status: TicketStatus = TicketStatus.open
    
    @Column(name = "assigned_to", length = 100)
    var assignedTo: String? = null
    
    @Column(name = "created_by", nullable = false, length = 100)
    var createdBy: String = ""
    
    @Column(name = "created_by_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var createdByType: CreatorType = CreatorType.admin
    
    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null
}

enum class TicketPriority {
    low,
    medium,
    high,
    urgent
}

enum class TicketStatus {
    open,
    in_progress,
    waiting_for_dealer,
    resolved,
    closed
}

enum class CreatorType {
    admin,
    dealer
}

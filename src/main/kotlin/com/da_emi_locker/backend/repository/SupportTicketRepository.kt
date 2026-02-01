package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.SupportTicket
import com.da_emi_locker.backend.entity.TicketStatus
import com.da_emi_locker.backend.entity.TicketPriority
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface SupportTicketRepository : JpaRepository<SupportTicket, Long> {
    
    fun findByTicketId(ticketId: String): Optional<SupportTicket>
    
    fun existsByTicketId(ticketId: String): Boolean
    
    // Get all tickets with optional filters
    @Query("""
        SELECT t FROM SupportTicket t
        WHERE (COALESCE(:status, NULL) IS NULL OR t.status = :status)
        AND (COALESCE(:priority, NULL) IS NULL OR t.priority = :priority)
        AND (COALESCE(:dealerId, '') = '' OR t.dealerId = :dealerId)
        AND (COALESCE(:search, '') = '' OR LOWER(t.subject) LIKE LOWER(CONCAT('%', :search, '%')) 
            OR LOWER(t.ticketId) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(t.dealerId) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    fun findAllWithFilters(
        @Param("status") status: TicketStatus?,
        @Param("priority") priority: TicketPriority?,
        @Param("dealerId") dealerId: String?,
        @Param("search") search: String?,
        pageable: Pageable
    ): Page<SupportTicket>
    
    // Get tickets for a specific dealer
    fun findByDealerIdOrderByCreatedAtDesc(dealerId: String, pageable: Pageable): Page<SupportTicket>
    
    // Count tickets by status
    fun countByStatus(status: TicketStatus): Long
    
    // Count tickets by dealer
    fun countByDealerId(dealerId: String): Long
}

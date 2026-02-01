package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.TicketMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TicketMessageRepository : JpaRepository<TicketMessage, Long> {
    
    fun findByTicketIdOrderByCreatedAtAsc(ticketId: String): List<TicketMessage>
    
    fun countByTicketId(ticketId: String): Long
    
    fun deleteByTicketId(ticketId: String)
}

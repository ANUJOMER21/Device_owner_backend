package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Contact form submission entity
 */
@Entity
@Table(name = "contact_submissions")
class ContactSubmission : BaseEntity() {
    
    @Column(nullable = false, length = 255)
    var name: String = ""
    
    @Column(nullable = false, length = 255)
    var email: String = ""
    
    @Column(length = 20)
    var phone: String? = null
    
    @Column(length = 255)
    var subject: String? = null
    
    @Column(nullable = false, columnDefinition = "TEXT")
    var message: String = ""
    
    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    var status: ContactStatus = ContactStatus.new
    
    @Column(name = "replied_at")
    var repliedAt: Instant? = null
}

enum class ContactStatus {
    new,
    read,
    replied,
    archived
}

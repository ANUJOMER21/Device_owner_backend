package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.ContactSubmission
import com.da_emi_locker.backend.entity.ContactStatus
import com.da_emi_locker.backend.repository.ContactSubmissionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ContactService(
    private val contactSubmissionRepository: ContactSubmissionRepository
) {
    
    data class ContactRequest(
        val name: String,
        val email: String,
        val phone: String? = null,
        val subject: String? = null,
        val message: String
    )
    
    data class ContactResponse(
        val success: Boolean,
        val message: String
    )
    
    @Transactional
    fun submitContactForm(request: ContactRequest): ContactResponse {
        // Validate email format
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"
        if (!request.email.matches(Regex(emailRegex))) {
            return ContactResponse(
                success = false,
                message = "Invalid email format"
            )
        }
        
        val submission = ContactSubmission().apply {
            this.name = request.name.trim()
            this.email = request.email.trim().lowercase()
            this.phone = request.phone?.trim()
            this.subject = request.subject?.trim()
            this.message = request.message.trim()
            this.status = ContactStatus.new
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }
        
        contactSubmissionRepository.save(submission)
        
        return ContactResponse(
            success = true,
            message = "Contact form submitted successfully. We will get back to you soon."
        )
    }
}

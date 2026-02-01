package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.ContactService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/contact")
class ContactController(
    private val contactService: ContactService
) {
    
    // ApiResponse wrapper for dealer app compatibility
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )
    
    // ContactFormResponseDto matching dealer app format
    data class ContactFormResponseDto(
        val messageId: String,
        val message: String
    )
    
    data class ContactRequestDto(
        @field:NotBlank(message = "Name is required")
        val name: String,
        
        @field:NotBlank(message = "Email is required")
        @field:Email(message = "Invalid email format")
        val email: String,
        
        val phone: String? = null,
        val subject: String? = null,
        
        @field:NotBlank(message = "Message is required")
        val message: String
    )
    
    @PostMapping
    fun submitContactForm(
        @RequestAttribute("dealerId") dealerId: String?,
        @Valid @RequestBody request: ContactRequestDto
    ): ResponseEntity<ApiResponse<ContactFormResponseDto>> {
        // Require authentication for dealer app
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = contactService.submitContactForm(
            ContactService.ContactRequest(
                name = request.name,
                email = request.email,
                phone = request.phone,
                subject = request.subject,
                message = request.message
            )
        )
        
        return if (response.success) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = ContactFormResponseDto(
                        messageId = UUID.randomUUID().toString(), // Generate message ID
                        message = "Your message has been submitted successfully"
                    )
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
}

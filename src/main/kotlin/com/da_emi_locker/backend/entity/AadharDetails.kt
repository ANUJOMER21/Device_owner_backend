package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.time.LocalDate

/**
 * Customer Aadhar details entity
 */
@Entity
@Table(name = "customer_aadhar_details")
class AadharDetails : BaseEntity() {
    
    @Column(name = "customer_id", unique = true, nullable = false, length = 50)
    var customerId: String = ""
    
    @Column(name = "aadhar_number", unique = true, nullable = false, length = 12)
    var aadharNumber: String = ""
    
    @Column(name = "full_name", nullable = false, length = 255)
    var fullName: String = ""
    
    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null
    
    @Column(columnDefinition = "TEXT")
    var address: String? = null
    
    @Column(name = "front_image_url", length = 500)
    var frontImageUrl: String? = null
    
    @Column(name = "back_image_url", length = 500)
    var backImageUrl: String? = null
    
    @Column(name = "verified")
    var verified: Boolean = false
    
    @Column(name = "verified_at")
    var verifiedAt: java.time.Instant? = null
}

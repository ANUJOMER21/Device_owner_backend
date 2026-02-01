package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.time.LocalDate

/**
 * Customer PAN details entity
 */
@Entity
@Table(name = "customer_pan_details")
class PANDetails : BaseEntity() {
    
    @Column(name = "customer_id", unique = true, nullable = false, length = 50)
    var customerId: String = ""
    
    @Column(name = "pan_number", unique = true, nullable = false, length = 10)
    var panNumber: String = ""
    
    @Column(name = "full_name", nullable = false, length = 255)
    var fullName: String = ""
    
    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null
    
    @Column(name = "father_name", length = 255)
    var fatherName: String? = null
    
    @Column(name = "image_url", length = 500)
    var imageUrl: String? = null
    
    @Column(name = "verified")
    var verified: Boolean = false
    
    @Column(name = "verified_at")
    var verifiedAt: java.time.Instant? = null
}

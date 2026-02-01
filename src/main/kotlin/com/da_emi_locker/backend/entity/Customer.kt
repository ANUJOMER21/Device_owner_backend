package com.da_emi_locker.backend.entity

import jakarta.persistence.*

/**
 * Customer entity representing a customer in the system
 */
@Entity
@Table(name = "customers")
class Customer : BaseEntity() {
    
    @Column(name = "customer_id", unique = true, nullable = false, length = 50)
    var customerId: String = ""
    
    @Column(name = "dealer_id", nullable = false, length = 50)
    var dealerId: String = ""
    
    @Column(nullable = false, length = 255)
    var name: String = ""
    
    @Column(length = 255)
    var email: String? = null
    
    @Column(nullable = false, length = 20)
    var phone: String = ""
    
    @Column(columnDefinition = "TEXT")
    var address: String? = null

    @Column(name = "customer_image_url", columnDefinition = "TEXT")
    var customerImageUrl: String? = null

    @Column(name = "signature_image_url", columnDefinition = "TEXT")
    var signatureImageUrl: String? = null
    
    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    var status: CustomerStatus = CustomerStatus.active
    
    @Column(name = "fcm_token", length = 500)
    var fcmToken: String? = null
    
    @Column(name = "aadhar_status", length = 20)
    var aadharStatus: String? = "not_submitted"
    
    @Column(name = "pan_status", length = 20)
    var panStatus: String? = "not_submitted"
    
    @Column(name = "loan_status", length = 20)
    var loanStatus: String? = "not_submitted"
    
    @Column(name = "imei1", unique = true, length = 20)
    var imei1: String? = null
    
    @Column(name = "imei2", length = 20)
    var imei2: String? = null

    @Column(name = "offline_unlock_code", unique = true, length = 32)
    var offlineUnlockCode: String? = null
}

enum class CustomerStatus {
    active,
    uninstalled,
    /** Customer created by dealer, device not yet activated by configure app */
    pending_activation,
    /** Device activated via configure app, app hidden from launcher */
    installed
}

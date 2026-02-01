package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Dealer entity representing a dealer/user in the system
 */
@Entity
@Table(name = "dealers")
class Dealer : BaseEntity() {
    
    @Column(name = "dealer_id", unique = true, nullable = false, length = 50)
    var dealerId: String = ""
    
    @Column(nullable = false, length = 255)
    var name: String = ""
    
    @Column(unique = true, nullable = false, length = 255)
    var email: String = ""
    
    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String = ""
    
    @Column(name = "plain_password", length = 255)
    var plainPassword: String? = null
    
    @Column(length = 20)
    var phone: String? = null
    
    @Column(columnDefinition = "TEXT")
    var address: String? = null
    
    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    var status: DealerStatus = DealerStatus.active
    
    @Column(name = "pin_hash", length = 255)
    var pinHash: String? = null
    
    @Column(name = "is_pin_set")
    var isPinSet: Boolean = false
    
    @Column(name = "last_login")
    var lastLogin: Instant? = null
    
    @Column(name = "customer_limit")
    var customerLimit: Int = 0
    
    @Column(name = "gst_number", unique = true, length = 15)
    var gstNumber: String? = null
    
    @Column(length = 100)
    var city: String? = null
    
    @Column(length = 100)
    var state: String? = null
    
    @Column(length = 10)
    var pincode: String? = null
    
    @Column(name = "business_name", length = 255)
    var businessName: String? = null
    
    @Column(name = "profile_image_url", length = 500)
    var profileImageUrl: String? = null
    
    @Column(name = "fcm_token", length = 500)
    var fcmToken: String? = null

    /** JWT jti of the current valid session; only one device can be logged in at a time. */
    @Column(name = "current_token_id", length = 255)
    var currentTokenId: String? = null

    /** DB-based login status: reliable flag set on login, cleared on logout. Used for admin display and login gate. */
    @Column(name = "is_logged_in", nullable = false)
    var isLoggedIn: Boolean = false
}

enum class DealerStatus {
    active,
    inactive,
    suspended
}

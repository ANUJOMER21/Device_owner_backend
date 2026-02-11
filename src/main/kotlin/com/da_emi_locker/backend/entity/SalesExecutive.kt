package com.da_emi_locker.backend.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * Sales Executive entity — a sub-user created by a dealer.
 * Shares the same app as the dealer but with restricted permissions
 * (can add customers, view details, EMI due, notifications; cannot send commands or view payment history).
 */
@Entity
@Table(name = "sales_executives")
class SalesExecutive : BaseEntity() {

    @Column(name = "sales_executive_id", unique = true, nullable = false, length = 50)
    var salesExecutiveId: String = ""

    @Column(name = "dealer_id", nullable = false, length = 50)
    var dealerId: String = ""

    @Column(nullable = false, length = 255)
    var name: String = ""

    @Column(nullable = false, length = 20)
    var phone: String = ""

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String = ""

    @Column(name = "plain_password", length = 255)
    var plainPassword: String? = null

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    var status: SalesExecutiveStatus = SalesExecutiveStatus.active

    /** Number of kits assigned to this SE from the dealer's pool */
    @Column(name = "assigned_kits", nullable = false)
    var assignedKits: Int = 0

    @Column(name = "pin_hash", length = 255)
    var pinHash: String? = null

    @Column(name = "is_pin_set", nullable = false)
    var isPinSet: Boolean = false

    /** JWT jti of the current valid session; only one device can be logged in at a time. */
    @Column(name = "current_token_id", length = 255)
    var currentTokenId: String? = null

    @Column(name = "is_logged_in", nullable = false)
    var isLoggedIn: Boolean = false

    @Column(name = "last_login")
    var lastLogin: Instant? = null
}

enum class SalesExecutiveStatus {
    active,
    inactive,
    suspended
}

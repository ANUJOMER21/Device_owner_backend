package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.*
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/admin/dealers")
class AdminDealerController(
    private val dealerService: DealerService,
    private val dealerPaymentService: DealerPaymentService,
    private val authenticationService: AuthenticationService
) {
    
    @GetMapping
    fun getAllDealers(
        @RequestParam(required = false) search: String?
    ): ResponseEntity<Map<String, Any>> {
        var dealers = dealerService.getAllDealers()
        
        // Apply search filter if provided
        if (!search.isNullOrBlank()) {
            val searchLower = search.lowercase()
            dealers = dealers.filter { dealer ->
                dealer.name.lowercase().contains(searchLower) ||
                dealer.email.lowercase().contains(searchLower) ||
                (dealer.phone?.lowercase()?.contains(searchLower) == true)
            }
        }
        
        val dealerList = dealers.map { dealer ->
            mapOf(
                "dealerId" to dealer.dealerId,
                "name" to dealer.name,
                "email" to dealer.email,
                "phone" to (dealer.phone ?: ""),
                "address" to (dealer.address ?: ""),
                "status" to dealer.status.name,
                "customerLimit" to dealer.customerLimit,
                "gstNumber" to (dealer.gstNumber ?: ""),
                "city" to (dealer.city ?: ""),
                "state" to (dealer.state ?: ""),
                "pincode" to (dealer.pincode ?: ""),
                "businessName" to (dealer.businessName ?: ""),
                "isPinSet" to dealer.isPinSet,
                "lastLogin" to (dealer.lastLogin?.toString() ?: ""),
                "isLoggedIn" to dealer.isLoggedIn,
                "password" to (dealer.plainPassword ?: ""),  // Plain password for admin viewing
                "createdAt" to (dealer.createdAt?.toString() ?: ""),
                "updatedAt" to (dealer.updatedAt?.toString() ?: "")
            )
        }
        
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "dealers" to dealerList,
            "total" to dealers.size
        ))
    }
    
    @GetMapping("/{dealerId}")
    fun getDealer(@PathVariable dealerId: String): ResponseEntity<Map<String, Any>> {
        val dealer = dealerService.getDealerInfo(dealerId)
        
        return if (dealer != null) {
            ResponseEntity.ok(mapOf(
                "success" to true,
                "dealer" to mapOf(
                    "dealerId" to dealer.dealerId,
                    "name" to dealer.name,
                    "email" to dealer.email,
                    "phone" to (dealer.phone ?: ""),
                    "address" to (dealer.address ?: ""),
                    "status" to dealer.status.name,
                    "customerLimit" to dealer.customerLimit,
                    "gstNumber" to (dealer.gstNumber ?: ""),
                    "city" to (dealer.city ?: ""),
                    "state" to (dealer.state ?: ""),
                    "pincode" to (dealer.pincode ?: ""),
                    "businessName" to (dealer.businessName ?: ""),
                    "isPinSet" to dealer.isPinSet,
                    "lastLogin" to (dealer.lastLogin?.toString() ?: ""),
                    "isLoggedIn" to dealer.isLoggedIn,
                    "password" to (dealer.plainPassword ?: ""),  // Plain password for admin viewing
                    "createdAt" to (dealer.createdAt?.toString() ?: ""),
                    "updatedAt" to (dealer.updatedAt?.toString() ?: "")
                )
            ))
        } else {
            ResponseEntity.status(404).body(mapOf(
                "success" to false,
                "message" to "Dealer not found"
            ))
        }
    }
    
    data class UpdateDealerRequestDto(
        val name: String? = null,
        val email: String? = null,
        val phone: String? = null,
        val address: String? = null,
        val status: String? = null,
        val customerLimit: Int? = null,
        val gstNumber: String? = null,
        val city: String? = null,
        val state: String? = null,
        val pincode: String? = null,
        val businessName: String? = null
    )
    
    @PutMapping("/{dealerId}")
    fun updateDealer(
        @PathVariable dealerId: String,
        @Valid @RequestBody request: UpdateDealerRequestDto
    ): ResponseEntity<DealerService.UpdateDealerResponse> {
        val response = dealerService.updateDealer(
            dealerId,
            DealerService.UpdateDealerRequest(
                name = request.name,
                email = request.email,
                phone = request.phone,
                address = request.address,
                status = request.status,
                customerLimit = request.customerLimit,
                gstNumber = request.gstNumber,
                city = request.city,
                state = request.state,
                pincode = request.pincode,
                businessName = request.businessName
            )
        )
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    /** Admin force logout: clears dealer session so they must login again. */
    @PostMapping("/{dealerId}/logout")
    fun forceLogout(@PathVariable dealerId: String): ResponseEntity<Map<String, Any>> {
        val success = authenticationService.logout(dealerId)
        return if (success) {
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Dealer logged out successfully"
            ))
        } else {
            ResponseEntity.status(404).body(mapOf(
                "success" to false,
                "message" to "Dealer not found"
            ))
        }
    }
    
    @DeleteMapping("/{dealerId}")
    fun deleteDealer(@PathVariable dealerId: String): ResponseEntity<DealerService.UpdateDealerResponse> {
        val response = dealerService.deleteDealer(dealerId)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(404).body(response)
        }
    }
    
    // Dealer Payment Endpoints
    
    data class CreatePaymentRequestDto(
        // Amount (money): positive = payment, negative/zero = reversal/removal; for removal send 0 or -refundAmount
        val amount: BigDecimal,
        
        @field:NotBlank(message = "Payment method is required")
        @field:Size(max = 50, message = "Payment method must be less than 50 characters")
        val paymentMethod: String,
        
        val transactionId: String? = null,
        val paymentDate: String? = null,
        val dueDate: String? = null,
        val description: String? = null,
        val notes: String? = null,
        /** Number of kits assigned (positive) or removed (required for removal). */
        val numberOfKits: Int? = null,
        /** Refund amount when reversing/removing kits; optional. */
        val refundAmount: BigDecimal? = null,
        /** Medium of refund (e.g. cash, UPI, bank_transfer); optional. */
        val refundMedium: String? = null
    )
    
    @PostMapping("/{dealerId}/payments")
    fun addPayment(
        @PathVariable dealerId: String,
        @Valid @RequestBody request: CreatePaymentRequestDto
    ): ResponseEntity<DealerPaymentService.PaymentResponse> {
        val response = dealerPaymentService.createPayment(
            DealerPaymentService.CreatePaymentRequest(
                dealerId = dealerId,
                amount = request.amount,
                paymentMethod = request.paymentMethod,
                transactionId = request.transactionId,
                paymentDate = request.paymentDate,
                dueDate = request.dueDate,
                description = request.description,
                notes = request.notes,
                numberOfKits = request.numberOfKits,
                refundAmount = request.refundAmount,
                refundMedium = request.refundMedium
            )
        )
        
        return if (response.success) {
            ResponseEntity.status(201).body(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    @GetMapping("/{dealerId}/payments")
    fun getDealerPayments(
        @PathVariable dealerId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<DealerPaymentService.PaymentListResponse> {
        val response = dealerPaymentService.getPaymentsByDealer(dealerId, page, pageSize)
        return ResponseEntity.ok(response)
    }
    
    // FCM Token Update
    data class UpdateFcmTokenRequestDto(
        @field:NotBlank(message = "FCM token is required")
        val fcmToken: String
    )
    
    @PutMapping("/{dealerId}/fcm-token")
    fun updateFcmToken(
        @PathVariable dealerId: String,
        @Valid @RequestBody request: UpdateFcmTokenRequestDto
    ): ResponseEntity<DealerService.UpdateDealerResponse> {
        val response = dealerService.updateFcmToken(dealerId, request.fcmToken)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
}

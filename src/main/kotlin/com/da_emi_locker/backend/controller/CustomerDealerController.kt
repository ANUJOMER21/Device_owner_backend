package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.DealerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Public dealer info for configure app (no auth).
 * Used after activation to show dealer details on device.
 */
@RestController
@RequestMapping("/api/customer/dealer")
class CustomerDealerController(
    private val dealerService: DealerService
) {
    
    @GetMapping("/{dealerId}")
    fun getDealerPublicInfo(@PathVariable dealerId: String): ResponseEntity<Map<String, Any>> {
        val dealer = dealerService.getDealerInfo(dealerId)
        return if (dealer != null) {
            ResponseEntity.ok(mapOf(
                "success" to true,
                "dealerId" to dealer.dealerId,
                "name" to dealer.name,
                "email" to (dealer.email ?: ""),
                "phone" to (dealer.phone ?: ""),
                "address" to (dealer.address ?: ""),
                "businessName" to (dealer.businessName ?: ""),
                "city" to (dealer.city ?: ""),
                "state" to (dealer.state ?: ""),
                "pincode" to (dealer.pincode ?: "")
            ))
        } else {
            ResponseEntity.status(404).body(mapOf(
                "success" to false,
                "message" to "Dealer not found"
            ))
        }
    }
}

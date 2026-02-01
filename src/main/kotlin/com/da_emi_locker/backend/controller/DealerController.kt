package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.service.DealerService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/dealers")
class DealerController(
    private val dealerService: DealerService,
    private val customerRepository: CustomerRepository
) {
    
    data class RegisterDealerRequestDto(
        @field:NotBlank(message = "Name is required")
        @field:Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
        val name: String,
        
        @field:NotBlank(message = "Email is required")
        @field:Email(message = "Invalid email format")
        val email: String,
        
        @field:NotBlank(message = "Password is required")
        @field:Size(min = 6, message = "Password must be at least 6 characters")
        val password: String,
        
        @field:NotBlank(message = "GST number is required")
        @field:Size(min = 15, max = 15, message = "GST number must be exactly 15 characters")
        val gstNumber: String,
        
        val phone: String? = null,
        val address: String? = null,
        val city: String? = null,
        val state: String? = null,
        val pincode: String? = null,
        val businessName: String? = null
    )
    
    data class SetCustomerLimitRequestDto(
        @field:Min(value = 0, message = "Customer limit must be 0 or greater")
        val customerLimit: Int
    )
    
    @PostMapping("/register")
    fun registerDealer(@Valid @RequestBody request: RegisterDealerRequestDto): ResponseEntity<DealerService.RegisterDealerResponse> {
        val response = dealerService.registerDealer(
            DealerService.RegisterDealerRequest(
                name = request.name,
                email = request.email,
                password = request.password,
                gstNumber = request.gstNumber,
                phone = request.phone,
                address = request.address,
                city = request.city,
                state = request.state,
                pincode = request.pincode,
                businessName = request.businessName
            )
        )
        
        return if (response.success) {
            ResponseEntity.status(201).body(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    @PostMapping("/{dealerId}/customer-limit")
    fun setCustomerLimit(
        @PathVariable dealerId: String,
        @Valid @RequestBody request: SetCustomerLimitRequestDto
    ): ResponseEntity<DealerService.SetCustomerLimitResponse> {
        val response = dealerService.setCustomerLimit(
            dealerId,
            DealerService.SetCustomerLimitRequest(request.customerLimit)
        )
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
    
    @GetMapping("/{dealerId}")
    fun getDealerInfo(@PathVariable dealerId: String): ResponseEntity<Map<String, Any>> {
        val dealer = dealerService.getDealerInfo(dealerId)
        
        return if (dealer != null) {
            ResponseEntity.ok(mapOf(
                "success" to true,
                "dealerId" to dealer.dealerId,
                "name" to dealer.name,
                "email" to dealer.email,
                "gstNumber" to (dealer.gstNumber ?: ""),
                "phone" to (dealer.phone ?: ""),
                "address" to (dealer.address ?: ""),
                "status" to dealer.status.name,
                "customerLimit" to dealer.customerLimit,
                "isPinSet" to dealer.isPinSet,
                "createdAt" to dealer.createdAt.toString()
            ))
        } else {
            ResponseEntity.status(404).body(mapOf(
                "success" to false,
                "message" to "Dealer not found"
            ))
        }
    }
    
    @GetMapping("/available-kits")
    fun getAvailableKits(
        @RequestAttribute("dealerId") dealerId: String?
    ): ResponseEntity<Map<String, Any>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(mapOf(
                "success" to false,
                "message" to "Unauthorized"
            ))
        }
        
        val dealer = dealerService.getDealerInfo(dealerId)
            ?: return ResponseEntity.status(404).body(mapOf(
                "success" to false,
                "message" to "Dealer not found"
            ))
        
        val currentCustomerCount = customerRepository.countByDealerId(dealerId)
        val totalKits = dealer.customerLimit
        val usedKits = currentCustomerCount.toInt()
        val availableKits = (totalKits - usedKits).coerceAtLeast(0)
        
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "totalKits" to totalKits,
            "usedKits" to usedKits,
            "availableKits" to availableKits,
            "dealerId" to dealerId
        ))
    }
}

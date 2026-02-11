package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.SalesExecutiveService
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Controller for dealer to manage their sales executives.
 * All endpoints require ROLE_DEALER.
 */
@RestController
@RequestMapping("/api/dealers/sales-executives")
class SalesExecutiveController(
    private val salesExecutiveService: SalesExecutiveService
) {

    data class CreateSERequestDto(
        @field:NotBlank(message = "Name is required")
        @field:Size(min = 2, max = 255)
        val name: String,

        @field:NotBlank(message = "Phone is required")
        @field:Pattern(regexp = "^[0-9]{10}$", message = "Phone must be 10 digits")
        val phone: String,

        @field:NotBlank(message = "Password is required")
        @field:Size(min = 6, message = "Password must be at least 6 characters")
        val password: String,

        @field:Min(0, message = "Assigned kits must be non-negative")
        val assignedKits: Int = 0
    )

    data class UpdateSERequestDto(
        val name: String? = null,
        val phone: String? = null,
        val assignedKits: Int? = null,
        val status: String? = null
    )

    @PostMapping
    fun create(
        @RequestAttribute("dealerId") dealerId: String?,
        @Valid @RequestBody request: CreateSERequestDto
    ): ResponseEntity<SalesExecutiveService.SalesExecutiveResponse> {
        if (dealerId == null) return unauthorized()
        val res = salesExecutiveService.createSalesExecutive(
            dealerId,
            SalesExecutiveService.CreateSalesExecutiveRequest(
                name = request.name,
                phone = request.phone,
                password = request.password,
                assignedKits = request.assignedKits
            )
        )
        return if (res.success) ResponseEntity.status(201).body(res) else ResponseEntity.badRequest().body(res)
    }

    @GetMapping
    fun list(@RequestAttribute("dealerId") dealerId: String?): ResponseEntity<SalesExecutiveService.SalesExecutiveListResponse> {
        if (dealerId == null) return ResponseEntity.status(401).body(
            SalesExecutiveService.SalesExecutiveListResponse(success = false, message = "Unauthorized")
        )
        val res = salesExecutiveService.listSalesExecutives(dealerId)
        return ResponseEntity.ok(res)
    }

    @GetMapping("/{seId}")
    fun get(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable seId: String
    ): ResponseEntity<SalesExecutiveService.SalesExecutiveResponse> {
        if (dealerId == null) return unauthorized()
        val res = salesExecutiveService.getSalesExecutive(dealerId, seId)
        return if (res.success) ResponseEntity.ok(res) else ResponseEntity.status(404).body(res)
    }

    @PutMapping("/{seId}")
    fun update(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable seId: String,
        @Valid @RequestBody request: UpdateSERequestDto
    ): ResponseEntity<SalesExecutiveService.SalesExecutiveResponse> {
        if (dealerId == null) return unauthorized()
        val res = salesExecutiveService.updateSalesExecutive(
            dealerId, seId,
            SalesExecutiveService.UpdateSalesExecutiveRequest(
                name = request.name,
                phone = request.phone,
                assignedKits = request.assignedKits,
                status = request.status
            )
        )
        return if (res.success) ResponseEntity.ok(res) else ResponseEntity.badRequest().body(res)
    }

    @DeleteMapping("/{seId}")
    fun delete(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable seId: String
    ): ResponseEntity<SalesExecutiveService.SalesExecutiveResponse> {
        if (dealerId == null) return unauthorized()
        val res = salesExecutiveService.deleteSalesExecutive(dealerId, seId)
        return if (res.success) ResponseEntity.ok(res) else ResponseEntity.badRequest().body(res)
    }

    @PostMapping("/{seId}/force-logout")
    fun forceLogout(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable seId: String
    ): ResponseEntity<SalesExecutiveService.SalesExecutiveResponse> {
        if (dealerId == null) return unauthorized()
        val res = salesExecutiveService.forceLogout(dealerId, seId)
        return if (res.success) ResponseEntity.ok(res) else ResponseEntity.badRequest().body(res)
    }

    private fun unauthorized() = ResponseEntity.status(401).body(
        SalesExecutiveService.SalesExecutiveResponse(success = false, message = "Unauthorized")
    )
}

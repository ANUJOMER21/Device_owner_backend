package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.AuthenticationService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthenticationController(
    private val authenticationService: AuthenticationService
) {

    // ApiResponse wrapper for dealer app compatibility
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )

    // LoginResponse matching dealer app format — includes role + dealerId
    data class LoginResponseData(
        val userId: String,
        val name: String,
        val email: String,
        val mobile: String?,
        val token: String,
        val isPinSet: Boolean,
        /** "dealer" or "sales_executive" */
        val role: String = "dealer",
        /** For SE: the parent dealer id; for dealer: same as userId */
        val dealerId: String? = null
    )

    data class SetPinResponseData(
        val isPinSet: Boolean
    )

    data class VerifyPinResponseData(
        val token: String,
        val userId: String
    )

    /**
     * Login now accepts phone + password (mobile-first).
     * Backward-compatible: also accepts email field which is treated as phone.
     */
    data class LoginRequestDto(
        /** Phone number (10 digits). Field kept as 'phone' but 'email' is also accepted for backward compat */
        val phone: String? = null,
        /** Backward-compat: if phone is null, use email field value as phone */
        val email: String? = null,

        @field:NotBlank(message = "Password is required")
        val password: String = ""
    )

    data class PinRequestDto(
        @field:NotBlank(message = "PIN is required")
        @field:Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4-6 digits")
        val pin: String
    )

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequestDto): ResponseEntity<ApiResponse<LoginResponseData>> {
        // Accept phone or fall back to email field for backward compat
        val phoneOrId = (request.phone?.takeIf { it.isNotBlank() } ?: request.email?.takeIf { it.isNotBlank() })
            ?: return ResponseEntity.status(400).body(
                ApiResponse(success = false, message = "Phone number is required")
            )

        val response = authenticationService.login(
            AuthenticationService.LoginRequest(
                phone = phoneOrId,
                password = request.password
            )
        )

        return if (response.success && response.data != null) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = LoginResponseData(
                        userId = response.data.userId,
                        name = response.data.name,
                        email = response.data.email,
                        mobile = response.data.mobile,
                        token = response.data.token,
                        isPinSet = response.data.isPinSet,
                        role = response.data.role,
                        dealerId = response.data.dealerId
                    )
                )
            )
        } else {
            ResponseEntity.status(401).body(
                ApiResponse(success = false, message = response.message)
            )
        }
    }

    @PostMapping("/logout")
    fun logout(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestAttribute(value = "salesExecutiveId", required = false) salesExecutiveId: String?
    ): ResponseEntity<ApiResponse<Boolean>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized", data = false)
            )
        }
        authenticationService.logout(dealerId, salesExecutiveId)
        return ResponseEntity.ok(
            ApiResponse(success = true, message = "Logged out successfully", data = true)
        )
    }

    @PostMapping("/set-pin")
    fun setPin(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestAttribute(value = "salesExecutiveId", required = false) salesExecutiveId: String?,
        @Valid @RequestBody request: PinRequestDto
    ): ResponseEntity<ApiResponse<SetPinResponseData>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized. Please login first.")
            )
        }

        val response = authenticationService.setPin(dealerId, AuthenticationService.PinRequest(request.pin), salesExecutiveId)

        return if (response.success) {
            ResponseEntity.ok(ApiResponse(success = true, message = response.message, data = SetPinResponseData(isPinSet = true)))
        } else {
            ResponseEntity.status(400).body(ApiResponse(success = false, message = response.message))
        }
    }

    @PostMapping("/verify-pin")
    fun verifyPin(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestAttribute(value = "salesExecutiveId", required = false) salesExecutiveId: String?,
        @Valid @RequestBody request: PinRequestDto
    ): ResponseEntity<ApiResponse<VerifyPinResponseData>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized. Please login first.")
            )
        }

        val response = authenticationService.verifyPin(dealerId, AuthenticationService.PinRequest(request.pin), salesExecutiveId)

        return if (response.success && response.token != null) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = VerifyPinResponseData(token = response.token, userId = salesExecutiveId ?: dealerId)
                )
            )
        } else {
            ResponseEntity.status(401).body(ApiResponse(success = false, message = response.message))
        }
    }
}

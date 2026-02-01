package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.ProfileService
import com.da_emi_locker.backend.service.S3StorageService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/dealers")
class ProfileController(
    private val profileService: ProfileService,
    private val s3StorageService: S3StorageService
) {
    
    // ApiResponse wrapper for dealer app compatibility
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )
    
    // DealerProfileDto matching dealer app format
    data class DealerProfileDto(
        val dealerId: String,
        val name: String,
        val email: String,
        val mobile: String,
        val address: String?,
        val city: String?,
        val state: String?,
        val pincode: String?,
        val profileImage: String?,
        val businessName: String?,
        val gstNumber: String?,
        val registrationDate: String
    )
    
    data class UpdateProfileRequestDto(
        @field:Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
        val name: String? = null,
        
        @field:Email(message = "Invalid email format")
        val email: String? = null,
        
        val mobile: String? = null,
        val address: String? = null,
        val city: String? = null,
        val state: String? = null,
        val pincode: String? = null,
        val businessName: String? = null,
        val gstNumber: String? = null,
        val profileImageUrl: String? = null
    )
    
    @GetMapping("/profile")
    fun getProfile(@RequestAttribute("dealerId") dealerId: String?): ResponseEntity<ApiResponse<DealerProfileDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = profileService.getProfile(dealerId)
        
        return if (response.success && response.profile != null) {
            val profileDto = DealerProfileDto(
                dealerId = response.profile.dealerId,
                name = response.profile.name,
                email = response.profile.email,
                mobile = response.profile.phone ?: "",
                address = response.profile.address,
                city = response.profile.city,
                state = response.profile.state,
                pincode = response.profile.pincode,
                profileImage = response.profile.profileImageUrl,
                businessName = response.profile.businessName,
                gstNumber = response.profile.gstNumber,
                registrationDate = response.profile.createdAt
            )
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = profileDto
                )
            )
        } else {
            ResponseEntity.status(404).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
    }
    
    @PutMapping("/profile")
    fun updateProfile(
        @RequestAttribute("dealerId") dealerId: String?,
        @Valid @RequestBody request: UpdateProfileRequestDto
    ): ResponseEntity<ApiResponse<DealerProfileDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = profileService.updateProfile(
            dealerId,
            ProfileService.UpdateProfileRequest(
                name = request.name,
                email = request.email,
                phone = request.mobile, // Map mobile to phone
                address = request.address,
                city = request.city,
                state = request.state,
                pincode = request.pincode,
                businessName = request.businessName,
                gstNumber = request.gstNumber,
                profileImageUrl = request.profileImageUrl
            )
        )
        
        return if (response.success && response.profile != null) {
            val profileDto = DealerProfileDto(
                dealerId = response.profile.dealerId,
                name = response.profile.name,
                email = response.profile.email,
                mobile = response.profile.phone ?: "",
                address = response.profile.address,
                city = response.profile.city,
                state = response.profile.state,
                pincode = response.profile.pincode,
                profileImage = response.profile.profileImageUrl,
                businessName = response.profile.businessName,
                gstNumber = response.profile.gstNumber,
                registrationDate = response.profile.createdAt
            )
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = profileDto
                )
            )
        } else {
            ResponseEntity.status(400).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
    }
    
    @PutMapping(value = ["/profile"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateProfileMultipart(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestParam("name") name: String,
        @RequestParam("mobile") mobile: String,
        @RequestParam("email") email: String,
        @RequestParam(value = "address", required = false) address: String?,
        @RequestParam(value = "city", required = false) city: String?,
        @RequestParam(value = "state", required = false) state: String?,
        @RequestParam(value = "pincode", required = false) pincode: String?,
        @RequestParam(value = "businessName", required = false) businessName: String?,
        @RequestParam(value = "gstNumber", required = false) gstNumber: String?,
        @RequestParam(value = "profile_image", required = false) profileImage: MultipartFile?
    ): ResponseEntity<ApiResponse<DealerProfileDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        var profileImageUrl: String? = null
        try {
            profileImage?.let { file ->
                if (!file.isEmpty) {
                    profileImageUrl = s3StorageService.uploadImage(file, "dealer_profile")
                        ?: throw IllegalStateException("S3 storage is not configured")
                }
            }
        } catch (e: Exception) {
            return ResponseEntity.status(500).body(
                ApiResponse(
                    success = false,
                    message = "Error uploading profile image: ${e.message}"
                )
            )
        }
        
        val response = profileService.updateProfile(
            dealerId,
            ProfileService.UpdateProfileRequest(
                name = name,
                email = email,
                phone = mobile,
                address = address,
                city = city,
                state = state,
                pincode = pincode,
                businessName = businessName,
                gstNumber = gstNumber,
                profileImageUrl = profileImageUrl
            )
        )
        
        return if (response.success && response.profile != null) {
            val profile = response.profile!!
            val profileDto = DealerProfileDto(
                dealerId = profile.dealerId,
                name = profile.name,
                email = profile.email,
                mobile = profile.phone ?: "",
                address = profile.address,
                city = profile.city,
                state = profile.state,
                pincode = profile.pincode,
                profileImage = profile.profileImageUrl,
                businessName = profile.businessName,
                gstNumber = profile.gstNumber,
                registrationDate = profile.createdAt
            )
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = profileDto
                )
            )
        } else {
            ResponseEntity.status(400).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
    }
    
}

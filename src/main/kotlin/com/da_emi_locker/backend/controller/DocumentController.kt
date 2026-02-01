package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.AadharService
import com.da_emi_locker.backend.service.LoanService
import com.da_emi_locker.backend.service.PANService
import com.da_emi_locker.backend.service.S3StorageService
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

@RestController
@RequestMapping("/api/customers/{customerId}")
class DocumentController(
    private val aadharService: AadharService,
    private val panService: PANService,
    private val loanService: LoanService,
    private val s3StorageService: S3StorageService
) {
    
    private val logger = LoggerFactory.getLogger(DocumentController::class.java)
    
    // ApiResponse wrapper for dealer app compatibility
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )
    
    // CustomerDetailsUpdateResponseDto matching dealer app format
    data class CustomerDetailsUpdateResponseDto(
        val customerId: String,
        val updateType: String, // aadhar, pan, loan
        val status: String, // pending, completed
        val message: String
    )
    
    data class AadharRequestDto(
        @field:NotBlank(message = "Aadhar number is required")
        @field:Pattern(regexp = "^[0-9]{12}$", message = "Aadhar number must be 12 digits")
        val aadharNumber: String,
        
        @field:NotBlank(message = "Full name is required")
        val fullName: String,
        
        val dateOfBirth: String? = null,
        val address: String? = null,
        val frontImageUrl: String? = null,
        val backImageUrl: String? = null
    )
    
    data class PANRequestDto(
        @field:NotBlank(message = "PAN number is required")
        @field:Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN format")
        val panNumber: String,
        
        @field:NotBlank(message = "Full name is required")
        val fullName: String,
        
        val dateOfBirth: String? = null,
        val fatherName: String? = null,
        val imageUrl: String? = null
    )
    
    data class LoanRequestDto(
        @field:NotNull(message = "Product price is required")
        @field:DecimalMin(value = "0.0", inclusive = false, message = "Product price must be greater than 0")
        val productPrice: BigDecimal?,
        
        @field:NotNull(message = "Down payment is required")
        @field:DecimalMin(value = "0.0", inclusive = true, message = "Down payment must be 0 or greater")
        val downPayment: BigDecimal?,
        
        val loanAmount: BigDecimal? = null,
        
        @field:NotNull(message = "Tenure is required")
        @field:Min(value = 1, message = "Tenure must be at least 1 month")
        val tenureMonths: Int?,
        
        @field:NotNull(message = "Rate of interest is required")
        @field:DecimalMin(value = "0.0", inclusive = true, message = "Rate of interest must be 0 or greater")
        val rateOfInterest: BigDecimal?,
        
        val monthlyEmi: BigDecimal? = null,
        val emiDate: String? = null,
        val remark: String? = null
    )
    
    @PostMapping("/aadhar")
    fun saveAadhar(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String,
        @Valid @RequestBody request: AadharRequestDto
    ): ResponseEntity<AadharService.AadharResponse> {
        if (dealerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                AadharService.AadharResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = aadharService.saveAadharDetails(
            dealerId,
            customerId,
            AadharService.AadharRequest(
                aadharNumber = request.aadharNumber,
                fullName = request.fullName,
                dateOfBirth = request.dateOfBirth,
                address = request.address,
                frontImageUrl = request.frontImageUrl,
                backImageUrl = request.backImageUrl
            )
        )
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
        }
    }
    
    @PostMapping(value = ["/aadhar"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun saveAadharMultipart(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String,
        @RequestParam("aadhar_number") aadharNumber: String,
        @RequestParam(value = "front_image", required = false) frontImage: MultipartFile?,
        @RequestParam(value = "back_image", required = false) backImage: MultipartFile?
    ): ResponseEntity<ApiResponse<CustomerDetailsUpdateResponseDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        var frontImageUrl: String? = null
        var backImageUrl: String? = null
        
        try {
            frontImage?.let { file ->
                if (!file.isEmpty) {
                    frontImageUrl = s3StorageService.uploadImage(file, "aadhar_front")
                        ?: throw IllegalStateException("S3 storage is not configured")
                }
            }

            backImage?.let { file ->
                if (!file.isEmpty) {
                    backImageUrl = s3StorageService.uploadImage(file, "aadhar_back")
                        ?: throw IllegalStateException("S3 storage is not configured")
                }
            }
        } catch (e: Exception) {
            logger.error("Error uploading Aadhar images", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse(
                    success = false,
                    message = "Error uploading images: ${e.message}"
                )
            )
        }
        
        val response = aadharService.saveAadharDetails(
            dealerId,
            customerId,
            AadharService.AadharRequest(
                aadharNumber = aadharNumber,
                fullName = "", // Will be updated separately if needed
                dateOfBirth = null,
                address = null,
                frontImageUrl = frontImageUrl,
                backImageUrl = backImageUrl
            )
        )
        
        return if (response.success) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = CustomerDetailsUpdateResponseDto(
                        customerId = customerId,
                        updateType = "aadhar",
                        status = "completed",
                        message = response.message
                    )
                )
            )
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
    }
    
    @GetMapping("/aadhar")
    fun getAadhar(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String
    ): ResponseEntity<AadharService.AadharResponse> {
        if (dealerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                AadharService.AadharResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = aadharService.getAadharDetails(customerId, dealerId)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
        }
    }
    
    @PostMapping("/pan")
    fun savePAN(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String,
        @Valid @RequestBody request: PANRequestDto
    ): ResponseEntity<PANService.PANResponse> {
        if (dealerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                PANService.PANResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = panService.savePANDetails(
            dealerId,
            customerId,
            PANService.PANRequest(
                panNumber = request.panNumber,
                fullName = request.fullName,
                dateOfBirth = request.dateOfBirth,
                fatherName = request.fatherName,
                imageUrl = request.imageUrl
            )
        )
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
        }
    }
    
    @PostMapping(value = ["/pan"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun savePANMultipart(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String,
        @RequestParam("pan_number") panNumber: String,
        @RequestParam(value = "pan_image", required = false) panImage: MultipartFile?
    ): ResponseEntity<ApiResponse<CustomerDetailsUpdateResponseDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        var panImageUrl: String? = null
        
        try {
            panImage?.let { file ->
                if (!file.isEmpty) {
                    panImageUrl = s3StorageService.uploadImage(file, "pan")
                        ?: throw IllegalStateException("S3 storage is not configured")
                }
            }
        } catch (e: Exception) {
            logger.error("Error uploading PAN image", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse(
                    success = false,
                    message = "Error uploading image: ${e.message}"
                )
            )
        }
        
        val response = panService.savePANDetails(
            dealerId,
            customerId,
            PANService.PANRequest(
                panNumber = panNumber,
                fullName = "", // Will be updated separately if needed
                dateOfBirth = null,
                fatherName = null,
                imageUrl = panImageUrl
            )
        )
        
        return if (response.success) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = CustomerDetailsUpdateResponseDto(
                        customerId = customerId,
                        updateType = "pan",
                        status = "completed",
                        message = response.message
                    )
                )
            )
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
    }
    
    @GetMapping("/pan")
    fun getPAN(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String
    ): ResponseEntity<PANService.PANResponse> {
        if (dealerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                PANService.PANResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = panService.getPANDetails(customerId, dealerId)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
        }
    }
    
    @PostMapping("/loan")
    fun saveLoan(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String,
        @Valid @RequestBody request: LoanRequestDto
    ): ResponseEntity<ApiResponse<CustomerDetailsUpdateResponseDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = loanService.saveLoanDetails(
            dealerId,
            customerId,
            LoanService.LoanRequest(
                productPrice = request.productPrice,
                downPayment = request.downPayment,
                loanAmount = request.loanAmount,
                tenureMonths = request.tenureMonths,
                rateOfInterest = request.rateOfInterest,
                monthlyEmi = request.monthlyEmi,
                emiDate = request.emiDate,
                remark = request.remark
            )
        )
        
        return if (response.success) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = CustomerDetailsUpdateResponseDto(
                        customerId = customerId,
                        updateType = "loan",
                        status = "completed",
                        message = response.message
                    )
                )
            )
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse(
                    success = false,
                    message = response.message
                )
            )
        }
    }
    
    @GetMapping("/loan")
    fun getLoan(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String
    ): ResponseEntity<LoanService.LoanResponse> {
        if (dealerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                LoanService.LoanResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = loanService.getLoanDetails(customerId, dealerId)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
        }
    }
}

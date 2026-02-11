package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.CustomerService
import com.da_emi_locker.backend.service.LoanService
import com.da_emi_locker.backend.service.S3StorageService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

@RestController
@RequestMapping("/api/customers")
class CustomerController(
    private val customerService: CustomerService,
    private val loanService: LoanService,
    private val s3StorageService: S3StorageService
) {
    
    // ApiResponse wrapper for dealer app compatibility
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )
    
    // CustomerDto matching dealer app format
    data class CustomerDto(
        val customerId: String,
        val name: String,
        val mobile: String,
        val alternateMobile: String? = null,
        val imei1: String,
        val imei2: String?,
        val loanType: String = "standard", // Default loan type
        val status: String,
        val createdDate: String,
        val productPrice: Double = 0.0,
        val downPayment: Double = 0.0,
        val monthlyEmi: Double = 0.0,
        val emiDate: String = "",
        val aadharStatus: String? = null,
        val panStatus: String? = null,
        val loanStatus: String? = null,
        val isOnline: Boolean = false,
        val lastSeen: String? = null,
        val offlineUnlockCode: String? = null,
        val customerImageUrl: String? = null,
        val signatureImageUrl: String? = null,
        val latitude: java.math.BigDecimal? = null,
        val longitude: java.math.BigDecimal? = null,
        val salesExecutiveId: String? = null,
        val salesExecutiveName: String? = null
    )
    
    private val logger = LoggerFactory.getLogger(CustomerController::class.java)
    
    data class CreateCustomerRequestDto(
        @field:NotBlank(message = "Name is required")
        @field:Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
        val name: String,
        
        @field:Email(message = "Invalid email format")
        val email: String? = null,
        
        @field:NotBlank(message = "Phone is required")
        @field:Pattern(regexp = "^[0-9]{10}$", message = "Phone must be exactly 10 digits")
        val phone: String,
        
        val address: String? = null,
        val customerImageUrl: String? = null,
        val signatureImageUrl: String? = null,
        
        @field:NotBlank(message = "IMEI1 is required")
        @field:Pattern(regexp = "^[0-9]{15}$", message = "IMEI1 must be exactly 15 digits")
        val imei1: String,
        
        @field:Pattern(regexp = "^[0-9]{15}$", message = "IMEI2 must be exactly 15 digits if provided")
        val imei2: String? = null
    )
    
    data class UpdateCustomerRequestDto(
        @field:Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
        val name: String? = null,
        
        @field:Email(message = "Invalid email format")
        val email: String? = null,
        
        @field:Pattern(regexp = "^[0-9]{10}$", message = "Phone must be exactly 10 digits")
        val phone: String? = null,
        
        val address: String? = null,
        val status: String? = null,
        val customerImageUrl: String? = null,
        val signatureImageUrl: String? = null
    )
    
    @GetMapping
    fun getCustomers(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<List<CustomerDto>>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = customerService.getCustomers(dealerId, status, page, size)
        
        return if (response.success && response.customers != null) {
            val customerDtos = response.customers.map { customerData ->
                mapToCustomerDto(customerData, dealerId)
            }
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = customerDtos
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
    
    @GetMapping("/{customerId}")
    fun getCustomer(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String
    ): ResponseEntity<ApiResponse<CustomerDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = customerService.getCustomer(customerId, dealerId)
        
        return if (response.success && response.customer != null) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = mapToCustomerDto(response.customer, dealerId)
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
    
    @PostMapping
    fun createCustomer(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestAttribute(value = "salesExecutiveId", required = false) salesExecutiveId: String?,
        @Valid @RequestBody request: CreateCustomerRequestDto
    ): ResponseEntity<ApiResponse<CustomerDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = customerService.createCustomer(
            dealerId,
            CustomerService.CreateCustomerRequest(
                name = request.name,
                email = request.email,
                phone = request.phone,
                address = request.address,
                customerImageUrl = request.customerImageUrl,
                signatureImageUrl = request.signatureImageUrl,
                imei1 = request.imei1,
                imei2 = request.imei2
            ),
            salesExecutiveId = salesExecutiveId
        )
        
        return if (response.success && response.customer != null) {
            ResponseEntity.status(201).body(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = mapToCustomerDto(response.customer, dealerId)
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
    
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createCustomerMultipart(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestAttribute(value = "salesExecutiveId", required = false) salesExecutiveId: String?,
        @RequestParam("name") name: String,
        @RequestParam("mobile") mobile: String,
        @RequestParam(value = "alternate_mobile", required = false) alternateMobile: String?,
        @RequestParam("imei1") imei1: String,
        @RequestParam(value = "imei2", required = false) imei2: String?,
        @RequestParam(value = "loan_type", required = false) loanType: String?,
        @RequestParam(value = "product_price", required = false) productPrice: String?,
        @RequestParam(value = "down_payment", required = false) downPayment: String?,
        @RequestParam(value = "tenure", required = false) tenure: String?,
        @RequestParam(value = "rate_of_interest", required = false) rateOfInterest: String?,
        @RequestParam(value = "emi_date", required = false) emiDate: String?,
        @RequestParam(value = "customer_image", required = false) customerImage: MultipartFile?,
        @RequestParam(value = "signature_image", required = false) signatureImage: MultipartFile?
    ): ResponseEntity<ApiResponse<CustomerDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        // Upload images and get URLs
        var customerImageUrl: String? = null
        var signatureImageUrl: String? = null
        
        try {
            customerImage?.let { file ->
                if (!file.isEmpty) {
                    customerImageUrl = s3StorageService.uploadImage(file, "customer")
                        ?: throw IllegalStateException("S3 storage is not configured")
                }
            }

            signatureImage?.let { file ->
                if (!file.isEmpty) {
                    signatureImageUrl = s3StorageService.uploadImage(file, "signature")
                        ?: throw IllegalStateException("S3 storage is not configured")
                }
            }
        } catch (e: Exception) {
            logger.error("Error uploading files", e)
            return ResponseEntity.status(500).body(
                ApiResponse<CustomerDto>(
                    success = false,
                    message = "Error uploading files: ${e.message}"
                )
            )
        }
        
        val response = customerService.createCustomer(
            dealerId,
            CustomerService.CreateCustomerRequest(
                name = name,
                email = null,
                phone = mobile,
                address = null,
                customerImageUrl = customerImageUrl,
                signatureImageUrl = signatureImageUrl,
                imei1 = imei1,
                imei2 = imei2
            ),
            salesExecutiveId = salesExecutiveId
        )
        
        // If loan details provided, save them
        if (loanType != null && productPrice != null && response.success && response.customer != null) {
            try {
                val productPriceDecimal = productPrice.toBigDecimalOrNull()
                val downPaymentDecimal = downPayment?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                val tenureInt = tenure?.toIntOrNull() ?: 0
                val rateOfInterestDecimal = rateOfInterest?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                
                if (productPriceDecimal != null && productPriceDecimal > java.math.BigDecimal.ZERO) {
                    // Save loan details immediately after creating customer
                    loanService.saveLoanDetails(
                        dealerId,
                        response.customer!!.customerId,
                        LoanService.LoanRequest(
                            productPrice = productPriceDecimal,
                            downPayment = downPaymentDecimal,
                            tenureMonths = tenureInt,
                            rateOfInterest = rateOfInterestDecimal,
                            emiDate = emiDate
                        )
                    )
                }
            } catch (e: Exception) {
                logger.warn("Error saving loan details: ${e.message}")
            }
        }
        
        return if (response.success && response.customer != null) {
            ResponseEntity.status(201).body(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = mapToCustomerDto(response.customer, dealerId)
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
    
    private fun mapToCustomerDto(customerData: CustomerService.CustomerData, dealerId: String): CustomerDto {
        // Try to get loan details
        val loanResponse = try {
            loanService.getLoanDetails(customerData.customerId, dealerId)
        } catch (e: Exception) {
            null
        }
        
        val loanDetails = loanResponse?.loanDetails
        val deviceStatus = customerData.deviceStatus
        val isOnline = deviceStatus?.status?.lowercase() == "online"
        val lastSeen = deviceStatus?.lastSeen
        
        return CustomerDto(
            customerId = customerData.customerId,
            name = customerData.name,
            mobile = customerData.phone,
            alternateMobile = null, // Not stored in Customer entity yet
            imei1 = customerData.imei1,
            imei2 = customerData.imei2,
            loanType = "standard", // Default, could be derived from loan details if needed
            status = customerData.status,
            createdDate = customerData.createdAt,
            productPrice = loanDetails?.productPrice?.toDouble() ?: 0.0,
            downPayment = loanDetails?.downPayment?.toDouble() ?: 0.0,
            monthlyEmi = loanDetails?.monthlyEmi?.toDouble() ?: loanDetails?.emiAmount?.toDouble() ?: 0.0,
            emiDate = loanDetails?.emiDate?.toString() ?: "",
            aadharStatus = customerData.aadharStatus,
            panStatus = customerData.panStatus,
            loanStatus = customerData.loanStatus,
            isOnline = isOnline,
            lastSeen = lastSeen,
            offlineUnlockCode = customerData.offlineUnlockCode,
            customerImageUrl = customerData.customerImageUrl,
            signatureImageUrl = customerData.signatureImageUrl,
            latitude = customerData.deviceStatus?.latitude,
            longitude = customerData.deviceStatus?.longitude,
            salesExecutiveId = customerData.salesExecutiveId,
            salesExecutiveName = customerData.salesExecutiveName
        )
    }
    
    @PutMapping(value = ["/{customerId}/signature"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateSignature(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String,
        @RequestParam("signature_image") signatureImage: MultipartFile
    ): ResponseEntity<ApiResponse<CustomerDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(success = false, message = "Unauthorized")
            )
        }
        if (signatureImage.isEmpty) {
            return ResponseEntity.status(400).body(
                ApiResponse(success = false, message = "Signature image is required")
            )
        }
        return try {
            val signatureUrl = s3StorageService.uploadImage(signatureImage, "signature")
                ?: return ResponseEntity.status(503).body(
                    ApiResponse(success = false, message = "S3 storage is not configured")
                )
            val response = customerService.updateCustomer(
                customerId,
                dealerId,
                CustomerService.UpdateCustomerRequest(signatureImageUrl = signatureUrl)
            )
            if (response.success && response.customer != null) {
                ResponseEntity.ok(
                    ApiResponse(
                        success = true,
                        message = "Signature updated successfully",
                        data = mapToCustomerDto(response.customer!!, dealerId)
                    )
                )
            } else {
                ResponseEntity.status(400).body(
                    ApiResponse(success = false, message = response.message)
                )
            }
        } catch (e: Exception) {
            ResponseEntity.status(500).body(
                ApiResponse(success = false, message = "Failed to upload signature: ${e.message}")
            )
        }
    }

    @PutMapping("/{customerId}")
    fun updateCustomer(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String,
        @Valid @RequestBody request: UpdateCustomerRequestDto
    ): ResponseEntity<ApiResponse<CustomerDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = customerService.updateCustomer(
            customerId,
            dealerId,
            CustomerService.UpdateCustomerRequest(
                name = request.name,
                email = request.email,
                phone = request.phone,
                address = request.address,
                status = request.status,
                customerImageUrl = request.customerImageUrl,
                signatureImageUrl = request.signatureImageUrl
            )
        )
        
        return if (response.success && response.customer != null) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = mapToCustomerDto(response.customer, dealerId)
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
    
    @DeleteMapping("/{customerId}")
    fun deleteCustomer(
        @RequestAttribute("dealerId") dealerId: String?,
        @PathVariable customerId: String
    ): ResponseEntity<ApiResponse<Boolean>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        val response = customerService.deleteCustomer(customerId, dealerId)
        
        return if (response.success) {
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = response.message,
                    data = true
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
    
    @PutMapping("/{customerId}/fcm-token")
    fun updateFCMToken(
        @PathVariable customerId: String,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<CustomerService.CustomerResponse> {
        val fcmToken = request["fcmToken"] ?: return ResponseEntity.status(400).body(
            CustomerService.CustomerResponse(
                success = false,
                message = "FCM token is required"
            )
        )
        
        val response = customerService.updateFCMToken(customerId, fcmToken)
        
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.status(400).body(response)
        }
    }
}

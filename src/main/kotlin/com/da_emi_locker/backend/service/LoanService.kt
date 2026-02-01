package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.Activity
import com.da_emi_locker.backend.entity.LoanDetails
import com.da_emi_locker.backend.entity.LoanStatus
import com.da_emi_locker.backend.entity.Customer
import com.da_emi_locker.backend.repository.ActivityRepository
import com.da_emi_locker.backend.repository.LoanDetailsRepository
import com.da_emi_locker.backend.repository.CustomerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class LoanService(
    private val loanDetailsRepository: LoanDetailsRepository,
    private val customerRepository: CustomerRepository,
    private val activityRepository: ActivityRepository
) {
    
    data class LoanRequest(
        val productPrice: BigDecimal? = null,
        val downPayment: BigDecimal? = null,
        val loanAmount: BigDecimal? = null,
        val tenureMonths: Int? = null,
        val rateOfInterest: BigDecimal? = null,
        val monthlyEmi: BigDecimal? = null,
        val emiDate: String? = null,
        val remark: String? = null
    )
    
    data class LoanResponse(
        val success: Boolean,
        val message: String,
        val loanDetails: LoanData? = null
    )
    
    data class LoanData(
        val loanId: String,
        val customerId: String,
        val productPrice: BigDecimal?,
        val downPayment: BigDecimal?,
        val loanAmount: BigDecimal?,
        val principalAmount: BigDecimal,
        val interestRate: BigDecimal,
        val tenureMonths: Int,
        val emiAmount: BigDecimal,
        val monthlyEmi: BigDecimal?,
        val startDate: String?,
        val endDate: String?,
        val emiDate: String?,
        val status: String,
        val totalPaid: BigDecimal,
        val remainingAmount: BigDecimal,
        val remark: String?,
        val createdAt: String,
        val updatedAt: String
    )
    
    @Transactional
    fun saveLoanDetails(dealerId: String, customerId: String, request: LoanRequest): LoanResponse {
        // Verify customer belongs to dealer
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return LoanResponse(
                success = false,
                message = "Customer not found"
            )
        
        if (customer.dealerId != dealerId) {
            return LoanResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        // Validate required fields
        if (request.productPrice == null || request.downPayment == null || request.tenureMonths == null || request.rateOfInterest == null) {
            return LoanResponse(
                success = false,
                message = "Product price, down payment, tenure, and rate of interest are required"
            )
        }
        
        // Calculate loan amount if not provided
        val loanAmount = request.loanAmount ?: (request.productPrice - request.downPayment)
        
        if (loanAmount <= BigDecimal.ZERO) {
            return LoanResponse(
                success = false,
                message = "Loan amount must be greater than zero"
            )
        }
        
        // Calculate EMI if not provided
        val monthlyEmi = request.monthlyEmi ?: calculateEMI(loanAmount, request.rateOfInterest, request.tenureMonths)
        
        // Parse dates
        val startDate = LocalDate.now()
        val endDate = startDate.plusMonths(request.tenureMonths.toLong())
        val emiDate = request.emiDate?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                return LoanResponse(
                    success = false,
                    message = "Invalid EMI date format. Use YYYY-MM-DD"
                )
            }
        } ?: startDate.plusDays(1) // Default to next day
        
        // Get or create loan details
        val existingLoans = loanDetailsRepository.findByCustomerIdAndStatus(customerId, LoanStatus.active)
        val loanDetails = if (existingLoans.isNotEmpty()) {
            existingLoans.first() // Update existing active loan
        } else {
            LoanDetails().apply {
                this.customerId = customerId
                this.loanId = generateLoanId()
                this.createdAt = Instant.now()
                this.updatedAt = Instant.now()
            }
        }
        
        // Update loan details
        loanDetails.productPrice = request.productPrice
        loanDetails.downPayment = request.downPayment
        loanDetails.loanAmount = loanAmount
        loanDetails.principalAmount = loanAmount
        loanDetails.interestRate = request.rateOfInterest
        loanDetails.rateOfInterest = request.rateOfInterest
        loanDetails.tenureMonths = request.tenureMonths
        loanDetails.emiAmount = monthlyEmi
        loanDetails.monthlyEmi = monthlyEmi
        loanDetails.startDate = startDate
        loanDetails.endDate = endDate
        loanDetails.emiDate = emiDate
        loanDetails.remark = request.remark
        loanDetails.remainingAmount = loanAmount - loanDetails.totalPaid
        loanDetails.status = LoanStatus.active
        loanDetails.updatedAt = Instant.now()
        
        val savedLoan = loanDetailsRepository.save(loanDetails)
        
        // Update customer status
        customer.loanStatus = "completed"
        customer.updatedAt = Instant.now()
        customerRepository.save(customer)
        
        // Log activity
        val activity = Activity().apply {
            this.customerId = customerId
            this.activityType = "loan_updated"
            this.activityDescription = "Loan details updated for customer ${customer.name}. Loan amount: ${loanAmount}, EMI: ${monthlyEmi}"
            this.createdAt = Instant.now()
        }
        activityRepository.save(activity)
        
        return LoanResponse(
            success = true,
            message = "Loan details saved successfully",
            loanDetails = LoanData(
                loanId = savedLoan.loanId,
                customerId = savedLoan.customerId,
                productPrice = savedLoan.productPrice,
                downPayment = savedLoan.downPayment,
                loanAmount = savedLoan.loanAmount,
                principalAmount = savedLoan.principalAmount,
                interestRate = savedLoan.interestRate,
                tenureMonths = savedLoan.tenureMonths,
                emiAmount = savedLoan.emiAmount,
                monthlyEmi = savedLoan.monthlyEmi,
                startDate = savedLoan.startDate?.toString(),
                endDate = savedLoan.endDate?.toString(),
                emiDate = savedLoan.emiDate?.toString(),
                status = savedLoan.status.name,
                totalPaid = savedLoan.totalPaid,
                remainingAmount = savedLoan.remainingAmount,
                remark = savedLoan.remark,
                createdAt = savedLoan.createdAt?.toString() ?: "",
                updatedAt = savedLoan.updatedAt?.toString() ?: ""
            )
        )
    }
    
    fun getLoanDetails(customerId: String, dealerId: String): LoanResponse {
        // Verify customer belongs to dealer
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return LoanResponse(
                success = false,
                message = "Customer not found"
            )
        
        if (customer.dealerId != dealerId) {
            return LoanResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        val loans = loanDetailsRepository.findByCustomerId(customerId)
        val activeLoan = loans.firstOrNull { it.status == LoanStatus.active }
            ?: loans.firstOrNull()
            ?: return LoanResponse(
                success = false,
                message = "Loan details not found"
            )
        
        return LoanResponse(
            success = true,
            message = "Loan details retrieved successfully",
            loanDetails = LoanData(
                loanId = activeLoan.loanId,
                customerId = activeLoan.customerId,
                productPrice = activeLoan.productPrice,
                downPayment = activeLoan.downPayment,
                loanAmount = activeLoan.loanAmount,
                principalAmount = activeLoan.principalAmount,
                interestRate = activeLoan.interestRate,
                tenureMonths = activeLoan.tenureMonths,
                emiAmount = activeLoan.emiAmount,
                monthlyEmi = activeLoan.monthlyEmi,
                startDate = activeLoan.startDate?.toString(),
                endDate = activeLoan.endDate?.toString(),
                emiDate = activeLoan.emiDate?.toString(),
                status = activeLoan.status.name,
                totalPaid = activeLoan.totalPaid,
                remainingAmount = activeLoan.remainingAmount,
                remark = activeLoan.remark,
                createdAt = activeLoan.createdAt?.toString() ?: "",
                updatedAt = activeLoan.updatedAt?.toString() ?: ""
            )
        )
    }
    
    private fun calculateEMI(principal: BigDecimal, rateOfInterest: BigDecimal, tenureMonths: Int): BigDecimal {
        if (rateOfInterest == BigDecimal.ZERO) {
            return principal.divide(BigDecimal.valueOf(tenureMonths.toLong()), 2, RoundingMode.HALF_UP)
        }
        
        val monthlyRate = rateOfInterest.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP)
        val emi = principal.multiply(monthlyRate)
            .multiply(
                BigDecimal.ONE.add(monthlyRate).pow(tenureMonths)
            )
            .divide(
                BigDecimal.ONE.add(monthlyRate).pow(tenureMonths).subtract(BigDecimal.ONE),
                2,
                RoundingMode.HALF_UP
            )
        
        return emi
    }
    
    private fun generateLoanId(): String {
        val timestamp = System.currentTimeMillis().toString().takeLast(8)
        val random = UUID.randomUUID().toString().substring(0, 4).uppercase().replace("-", "")
        return "LOAN$timestamp$random"
    }
}

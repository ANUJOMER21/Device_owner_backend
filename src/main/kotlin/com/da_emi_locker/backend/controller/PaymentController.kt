package com.da_emi_locker.backend.controller

import com.da_emi_locker.backend.service.DealerPaymentService
import com.da_emi_locker.backend.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService,
    private val dealerPaymentService: DealerPaymentService
) {
    
    // ApiResponse wrapper for dealer app compatibility
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null
    )
    
    // PaymentHistoryDto matching dealer app format (no status - removed per requirement)
    data class PaymentHistoryDto(
        val paymentId: String,
        val orderId: String,
        val kitType: String,
        val quantity: Int,
        val amount: Double,
        val paymentDate: String,
        val paymentMethod: String,
        val transactionId: String,
        /** Refund amount when this is a reversal; null otherwise. */
        val refundAmount: Double? = null,
        /** Medium of refund (e.g. cash, UPI, bank_transfer); null otherwise. */
        val refundMedium: String? = null
    )
    
    // Wrapper with totals from API
    data class PaymentHistoryResponseDto(
        val payments: List<PaymentHistoryDto>,
        val totalAmount: Double,
        val totalKits: Int
    )
    
    @GetMapping("/history")
    fun getPaymentHistory(
        @RequestAttribute("dealerId") dealerId: String?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<ApiResponse<PaymentHistoryResponseDto>> {
        if (dealerId == null) {
            return ResponseEntity.status(401).body(
                ApiResponse(
                    success = false,
                    message = "Unauthorized"
                )
            )
        }
        
        // Get dealer payments (kit purchases and reversals)
        val dealerPaymentsResponse = dealerPaymentService.getDealerPaymentsForAdmin(
            dealerId,
            startDate,
            endDate,
            page,
            pageSize
        )
        
        // Map to app's expected format; quantity = numberOfKits (positive for assignment, negative for reversal)
        val paymentDtos = dealerPaymentsResponse.payments.map { payment ->
            val amt = payment.amount.toDouble()
            val kitCount = payment.numberOfKits ?: if (amt >= 0) 1 else -1
            val qty = if (amt >= 0) kitCount else -kitCount
            PaymentHistoryDto(
                paymentId = payment.paymentId,
                orderId = payment.transactionId ?: payment.paymentId,
                kitType = if (amt >= 0) "standard" else "reversal",
                quantity = qty,
                amount = amt,
                paymentDate = payment.paymentDate ?: "",
                paymentMethod = payment.paymentMethod,
                transactionId = payment.transactionId ?: "",
                refundAmount = payment.refundAmount?.toDouble(),
                refundMedium = payment.refundMedium
            )
        }
        
        // Totals from API (all-time for this dealer)
        val (totalAmountBd, totalKitsLong) = dealerPaymentService.getDealerPaymentTotals(dealerId)
        val totalAmount = totalAmountBd.toDouble()
        val totalKits = totalKitsLong.toInt()
        
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Payment history retrieved successfully",
                data = PaymentHistoryResponseDto(
                    payments = paymentDtos,
                    totalAmount = totalAmount,
                    totalKits = totalKits
                )
            )
        )
    }
}

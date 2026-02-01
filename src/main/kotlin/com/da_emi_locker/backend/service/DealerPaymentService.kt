package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.DealerPayment
import com.da_emi_locker.backend.entity.PaymentStatus
import com.da_emi_locker.backend.repository.DealerPaymentRepository
import com.da_emi_locker.backend.repository.DealerRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
class DealerPaymentService(
    private val dealerPaymentRepository: DealerPaymentRepository,
    private val dealerRepository: DealerRepository
) {
    
    data class CreatePaymentRequest(
        val dealerId: String,
        val amount: BigDecimal,
        val paymentMethod: String,
        val transactionId: String? = null,
        val paymentDate: String? = null,
        val dueDate: String? = null,
        val description: String? = null,
        val notes: String? = null,
        val numberOfKits: Int? = null,
        /** Refund amount when this is a kit removal/reversal; optional. */
        val refundAmount: BigDecimal? = null,
        /** Medium of refund (e.g. cash, UPI, bank_transfer); optional. */
        val refundMedium: String? = null
    )
    
    data class PaymentResponse(
        val success: Boolean,
        val message: String,
        val payment: PaymentData? = null
    )
    
    data class PaymentListResponse(
        val success: Boolean,
        val message: String,
        val payments: List<PaymentData> = emptyList(),
        val total: Long? = null,
        val page: Int? = null,
        val pageSize: Int? = null,
        val totalPages: Int? = null
    )
    
    data class PaymentData(
        val paymentId: String,
        val dealerId: String,
        val amount: BigDecimal,
        val paymentMethod: String,
        val paymentStatus: String,
        val transactionId: String?,
        val paymentDate: String?,
        val dueDate: String?,
        val description: String?,
        val notes: String?,
        val numberOfKits: Int?,
        val refundAmount: BigDecimal?,
        val refundMedium: String?,
        val createdAt: String,
        val updatedAt: String
    )
    
    @Transactional
    fun createPayment(request: CreatePaymentRequest): PaymentResponse {
        // Verify dealer exists
        dealerRepository.findByDealerId(request.dealerId)
            .orElse(null) ?: return PaymentResponse(
                success = false,
                message = "Dealer not found"
            )
        
        // Validate amount - allow zero only for kit removal (adjustment) without refund
        if (request.amount == BigDecimal.ZERO && request.numberOfKits == null) {
            return PaymentResponse(
                success = false,
                message = "Amount cannot be zero"
            )
        }
        
        // Generate payment ID
        val paymentId = generatePaymentId()
        
        // Parse dates - handle both ISO timestamps and date-only strings (YYYY-MM-DD)
        val paymentDate = request.paymentDate?.let {
            try {
                // Try parsing as ISO timestamp first
                Instant.parse(it)
            } catch (e: Exception) {
                try {
                    // Try parsing as date-only string (YYYY-MM-DD) and convert to start of day UTC
                    java.time.LocalDate.parse(it).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
                } catch (e2: Exception) {
                    // Fallback to current time if both fail
                    Instant.now()
                }
            }
        } ?: Instant.now()
        
        val dueDate = request.dueDate?.let {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                null
            }
        }
        
        // Kit count: for removal (amount <= 0) require numberOfKits; for assignment use request or default 1
        if (request.amount.signum() <= 0 && request.numberOfKits == null) {
            return PaymentResponse(success = false, message = "Number of kits is required for kit removal/reversal")
        }
        val kitCount = request.numberOfKits
            ?: if (request.amount.signum() < 0) request.amount.abs().toInt().coerceAtLeast(1) else 1

        // Create payment
        val payment = DealerPayment().apply {
            this.dealerId = request.dealerId
            this.paymentId = paymentId
            this.amount = request.amount
            this.paymentMethod = request.paymentMethod
            this.transactionId = request.transactionId
            this.paymentDate = paymentDate
            this.dueDate = dueDate
            this.description = request.description
            this.notes = request.notes
            this.numberOfKits = kitCount
            this.refundAmount = request.refundAmount
            this.refundMedium = request.refundMedium
            this.paymentStatus = if (request.amount.signum() < 0) PaymentStatus.refunded else PaymentStatus.completed
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }
        
        val savedPayment = dealerPaymentRepository.save(payment)
        
        return PaymentResponse(
            success = true,
            message = "Payment added successfully",
            payment = mapToPaymentData(savedPayment)
        )
    }
    
    fun getPaymentsByDealer(
        dealerId: String,
        page: Int = 0,
        pageSize: Int = 20
    ): PaymentListResponse {
        val pageable: Pageable = PageRequest.of(page, pageSize)
        val payments = dealerPaymentRepository.findByDealerId(dealerId, pageable)
        
        val paymentDataList = payments.content.map { mapToPaymentData(it) }
        
        return PaymentListResponse(
            success = true,
            message = "Payments retrieved successfully",
            payments = paymentDataList,
            total = payments.totalElements,
            page = payments.number,
            pageSize = payments.size,
            totalPages = payments.totalPages
        )
    }

    fun getDealerPaymentsForAdmin(
        dealerId: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        page: Int = 0,
        pageSize: Int = 20
    ): PaymentListResponse {
        val pageable: Pageable = PageRequest.of(page, pageSize)

        val (startInstant, endInstant) = parseDateRange(startDate, endDate)

        val payments: Page<DealerPayment> = when {
            dealerId != null && startInstant != null && endInstant != null ->
                dealerPaymentRepository.findByDealerIdAndPaymentDateBetween(dealerId, startInstant, endInstant, pageable)
            dealerId != null ->
                dealerPaymentRepository.findByDealerId(dealerId, pageable)
            startInstant != null && endInstant != null ->
                dealerPaymentRepository.findByPaymentDateBetween(startInstant, endInstant, pageable)
            else ->
                dealerPaymentRepository.findAll(pageable)
        }

        val paymentDataList = payments.content.map { mapToPaymentData(it) }
        return PaymentListResponse(
            success = true,
            message = "Payments retrieved successfully",
            payments = paymentDataList,
            total = payments.totalElements,
            page = payments.number,
            pageSize = payments.size,
            totalPages = payments.totalPages
        )
    }
    
    /** Totals for a dealer (all-time): totalAmount = sum(amount), totalKits = sum(numberOfKits) for positive - sum for negative */
    fun getDealerPaymentTotals(dealerId: String): Pair<BigDecimal, Long> {
        val totalAmount = dealerPaymentRepository.sumAmountByDealerId(dealerId) ?: BigDecimal.ZERO
        val kitsAdded = dealerPaymentRepository.sumKitCountForPositiveAmount(dealerId) ?: dealerPaymentRepository.countByDealerIdAndAmountGreaterThanEqual(dealerId, BigDecimal.ZERO)
        val kitsReversed = dealerPaymentRepository.sumKitCountForNegativeAmount(dealerId) ?: dealerPaymentRepository.countByDealerIdAndAmountLessThan(dealerId, BigDecimal.ZERO)
        val totalKits = kitsAdded - kitsReversed
        return Pair(totalAmount, totalKits)
    }
    
    /** Reverse a payment (delete kit): creates a new payment record with negative amount for history. */
    @Transactional
    fun reversePayment(
        paymentId: String,
        reason: String? = null,
        refundAmount: BigDecimal? = null,
        refundMedium: String? = null
    ): PaymentResponse {
        val original = dealerPaymentRepository.findByPaymentId(paymentId)
            ?: return PaymentResponse(success = false, message = "Payment not found")
        if (original.amount.signum() <= 0) {
            return PaymentResponse(success = false, message = "Cannot reverse a reversal or zero payment")
        }
        val reverseAmount = (refundAmount ?: original.amount).negate()
        val reversePaymentId = generatePaymentId()
        val description = "Kit reversed. Ref: $paymentId. ${reason?.take(200) ?: ""}".trim()
        val payment = DealerPayment().apply {
            this.dealerId = original.dealerId
            this.paymentId = reversePaymentId
            this.amount = reverseAmount
            this.paymentMethod = "reversal"
            this.transactionId = "REV-$paymentId"
            this.paymentDate = Instant.now()
            this.description = description
            this.numberOfKits = original.numberOfKits ?: 1
            this.refundAmount = refundAmount
            this.refundMedium = refundMedium
            this.paymentStatus = PaymentStatus.refunded
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
        }
        dealerPaymentRepository.save(payment)
        return PaymentResponse(
            success = true,
            message = "Kit reversed successfully",
            payment = mapToPaymentData(payment)
        )
    }

    private fun parseDateRange(startDate: String?, endDate: String?): Pair<Instant?, Instant?> {
        if (startDate.isNullOrBlank() || endDate.isNullOrBlank()) return Pair(null, null)
        return try {
            // Accept yyyy-MM-dd
            val start = LocalDate.parse(startDate).atStartOfDay().toInstant(ZoneOffset.UTC)
            // inclusive end: endDate 23:59:59 UTC
            val end = LocalDate.parse(endDate).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1)
            Pair(start, end)
        } catch (_: Exception) {
            try {
                Pair(Instant.parse(startDate), Instant.parse(endDate))
            } catch (_: Exception) {
                Pair(null, null)
            }
        }
    }
    
    private fun mapToPaymentData(payment: DealerPayment): PaymentData {
        return PaymentData(
            paymentId = payment.paymentId,
            dealerId = payment.dealerId,
            amount = payment.amount,
            paymentMethod = payment.paymentMethod,
            paymentStatus = payment.paymentStatus.name,
            transactionId = payment.transactionId,
            paymentDate = payment.paymentDate?.toString(),
            dueDate = payment.dueDate?.toString(),
            description = payment.description,
            notes = payment.notes,
            numberOfKits = payment.numberOfKits,
            refundAmount = payment.refundAmount,
            refundMedium = payment.refundMedium,
            createdAt = payment.createdAt?.toString() ?: "",
            updatedAt = payment.updatedAt?.toString() ?: ""
        )
    }
    
    private fun generatePaymentId(): String {
        val timestamp = System.currentTimeMillis().toString().takeLast(8)
        val random = UUID.randomUUID().toString().substring(0, 4).uppercase().replace("-", "")
        return "DPY$timestamp$random"
    }
}

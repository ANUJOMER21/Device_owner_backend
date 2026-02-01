package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.PaymentHistory
import com.da_emi_locker.backend.entity.PaymentStatus
import com.da_emi_locker.backend.repository.PaymentHistoryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PaymentService(
    private val paymentHistoryRepository: PaymentHistoryRepository
) {
    
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
        val customerId: String,
        val loanId: String?,
        val amount: java.math.BigDecimal,
        val paymentMethod: String,
        val paymentStatus: String,
        val transactionId: String?,
        val paymentDate: String?,
        val dueDate: String?,
        val emiNumber: Int?,
        val notes: String?,
        val createdAt: String
    )
    
    fun getPaymentHistory(
        dealerId: String,
        status: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        page: Int = 0,
        pageSize: Int = 20
    ): PaymentListResponse {
        val pageable: Pageable = PageRequest.of(page, pageSize)
        
        val payments: Page<PaymentHistory> = when {
            status != null && startDate != null && endDate != null -> {
                try {
                    val paymentStatus = PaymentStatus.valueOf(status.lowercase())
                    val start = Instant.parse(startDate)
                    val end = Instant.parse(endDate)
                    paymentHistoryRepository.findByDealerIdAndDateRange(dealerId, start, end, pageable)
                        .let { page ->
                            // Filter by status in memory if needed
                            if (page.content.any { it.paymentStatus == paymentStatus }) {
                                // Note: This is a simplified approach. For better performance, 
                                // you might want to add a combined query in the repository
                                val filtered = page.content.filter { it.paymentStatus == paymentStatus }
                                org.springframework.data.domain.PageImpl(filtered, pageable, filtered.size.toLong())
                            } else {
                                page
                            }
                        }
                } catch (e: Exception) {
                    paymentHistoryRepository.findByDealerId(dealerId, pageable)
                }
            }
            status != null -> {
                try {
                    val paymentStatus = PaymentStatus.valueOf(status.lowercase())
                    paymentHistoryRepository.findByDealerIdAndStatus(dealerId, paymentStatus, pageable)
                } catch (e: Exception) {
                    paymentHistoryRepository.findByDealerId(dealerId, pageable)
                }
            }
            startDate != null && endDate != null -> {
                try {
                    val start = Instant.parse(startDate)
                    val end = Instant.parse(endDate)
                    paymentHistoryRepository.findByDealerIdAndDateRange(dealerId, start, end, pageable)
                } catch (e: Exception) {
                    paymentHistoryRepository.findByDealerId(dealerId, pageable)
                }
            }
            else -> {
                paymentHistoryRepository.findByDealerId(dealerId, pageable)
            }
        }
        
        val paymentDataList = payments.content.map { payment ->
            PaymentData(
                paymentId = payment.paymentId,
                customerId = payment.customerId,
                loanId = payment.loanId,
                amount = payment.amount,
                paymentMethod = payment.paymentMethod,
                paymentStatus = payment.paymentStatus.name,
                transactionId = payment.transactionId,
                paymentDate = payment.paymentDate?.toString(),
                dueDate = payment.dueDate?.toString(),
                emiNumber = payment.emiNumber,
                notes = payment.notes,
                createdAt = payment.createdAt?.toString() ?: ""
            )
        }
        
        return PaymentListResponse(
            success = true,
            message = "Payment history retrieved successfully",
            payments = paymentDataList,
            total = payments.totalElements,
            page = payments.number,
            pageSize = payments.size,
            totalPages = payments.totalPages
        )
    }
}

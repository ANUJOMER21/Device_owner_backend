package com.da_emi_locker.backend.service

import com.da_emi_locker.backend.entity.Activity
import com.da_emi_locker.backend.repository.ActivityRepository
import com.da_emi_locker.backend.repository.CustomerRepository
import com.da_emi_locker.backend.repository.DealerRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.util.NoSuchElementException

@Service
class ActivityService(
    private val activityRepository: ActivityRepository,
    private val customerRepository: CustomerRepository,
    private val dealerRepository: DealerRepository
) {
    
    data class ActivityListResponse(
        val success: Boolean,
        val message: String,
        val activities: List<ActivityData>? = null,
        val total: Long? = null,
        val page: Int? = null,
        val pageSize: Int? = null,
        val totalPages: Int? = null
    )
    
    data class ActivityResponse(
        val success: Boolean,
        val message: String,
        val activity: ActivityData? = null
    )
    
    data class ActivityData(
        val id: Long,
        val customerId: String?,
        val customerName: String? = null,
        val dealerId: String? = null,
        val dealerName: String? = null,
        val deviceId: String?,
        val activityType: String,
        val activityDescription: String?,
        val metadata: String?,
        val createdAt: String
    )

    data class ActivityTypesResponse(
        val success: Boolean,
        val message: String,
        val activityTypes: List<String> = emptyList()
    )
    
    fun getActivities(
        dealerId: String,
        activityType: String? = null,
        customerId: String? = null,
        page: Int = 0,
        size: Int = 20
    ): ActivityListResponse {
        // Get all customer IDs for this dealer
        val dealerCustomers = customerRepository.findByDealerId(dealerId)
        val customerIds = dealerCustomers.map { it.customerId }.toSet()
        
        // Build pageable
        val pageable: Pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        
        // Filter activities
        val allActivities = activityRepository.findAll(pageable)
        val filteredActivities = allActivities.content.filter { activity ->
            // Only show activities for dealer's customers
            val belongsToDealer = activity.customerId == null || customerIds.contains(activity.customerId)
            
            // Apply filters
            val typeMatches = activityType == null || activity.activityType == activityType
            val customerMatches = customerId == null || activity.customerId == customerId
            
            belongsToDealer && typeMatches && customerMatches
        }
        
        // Convert to response format
        val activityDataList = filteredActivities.map { activity ->
            ActivityData(
                id = activity.id ?: 0,
                customerId = activity.customerId,
                deviceId = activity.deviceId,
                activityType = activity.activityType,
                activityDescription = activity.activityDescription,
                metadata = activity.metadata,
                createdAt = activity.createdAt?.toString() ?: ""
            )
        }
        
        return ActivityListResponse(
            success = true,
            message = "Activities retrieved successfully",
            activities = activityDataList,
            total = allActivities.totalElements,
            page = allActivities.number,
            pageSize = allActivities.size,
            totalPages = allActivities.totalPages
        )
    }
    
    fun getCustomerActivities(
        dealerId: String,
        customerId: String,
        page: Int = 0,
        size: Int = 20
    ): ActivityListResponse {
        // Verify customer belongs to dealer
        val customer = customerRepository.findByCustomerId(customerId)
            .orElse(null) ?: return ActivityListResponse(
                success = false,
                message = "Customer not found"
            )
        
        if (customer.dealerId != dealerId) {
            return ActivityListResponse(
                success = false,
                message = "Access denied"
            )
        }
        
        // Get activities for this customer
        val activities = activityRepository.findByCustomerId(customerId)
        val sortedActivities = activities.sortedByDescending { it.createdAt }
        
        // Apply pagination manually
        val start = page * size
        val end = minOf(start + size, sortedActivities.size)
        val paginatedActivities = sortedActivities.subList(start, end)
        
        val activityDataList = paginatedActivities.map { activity ->
            ActivityData(
                id = activity.id ?: 0,
                customerId = activity.customerId,
                deviceId = activity.deviceId,
                activityType = activity.activityType,
                activityDescription = activity.activityDescription,
                metadata = activity.metadata,
                createdAt = activity.createdAt?.toString() ?: ""
            )
        }
        
        return ActivityListResponse(
            success = true,
            message = "Customer activities retrieved successfully",
            activities = activityDataList,
            total = activities.size.toLong(),
            page = page,
            pageSize = size,
            totalPages = (activities.size + size - 1) / size
        )
    }
    
    fun getActivity(activityId: Long, dealerId: String): ActivityResponse {
        val activityOpt = activityRepository.findById(activityId)
        if (activityOpt.isEmpty) {
            return ActivityResponse(
                success = false,
                message = "Activity not found"
            )
        }
        val activity = activityOpt.orElseThrow { NoSuchElementException("Activity not found") }
        
        // Verify activity belongs to dealer's customer
        val activityCustomerId = activity.customerId
        if (activityCustomerId != null) {
            val customer = customerRepository.findByCustomerId(activityCustomerId)
                .orElse(null)
            
            if (customer == null || customer.dealerId != dealerId) {
                return ActivityResponse(
                    success = false,
                    message = "Access denied"
                )
            }
        }
        
        return ActivityResponse(
            success = true,
            message = "Activity retrieved successfully",
            activity = ActivityData(
                id = activity.id ?: 0,
                customerId = activity.customerId,
                deviceId = activity.deviceId,
                activityType = activity.activityType,
                activityDescription = activity.activityDescription,
                metadata = activity.metadata,
                createdAt = activity.createdAt?.toString() ?: ""
            )
        )
    }
    
    // Admin method to get all activities for a specific dealer
    fun getDealerActivities(
        dealerId: String,
        activityType: String? = null,
        page: Int = 0,
        size: Int = 20
    ): ActivityListResponse {
        // Get all customer IDs for this dealer
        val dealerCustomers = customerRepository.findByDealerId(dealerId)
        val customerIds = dealerCustomers.map { it.customerId }.toSet()
        
        // Build pageable
        val pageable: Pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        
        // Filter activities
        val allActivities = activityRepository.findAll(pageable)
        val filteredActivities = allActivities.content.filter { activity ->
            // Only show activities for dealer's customers or dealer-related activities
            val metadataStr = activity.metadata
            val belongsToDealer = activity.customerId == null || customerIds.contains(activity.customerId) ||
                    (metadataStr != null && metadataStr.contains("\"dealer_id\":\"$dealerId\""))
            
            // Apply type filter
            val typeMatches = activityType == null || activity.activityType == activityType
            
            belongsToDealer && typeMatches
        }
        
        // Convert to response format
        val activityDataList = filteredActivities.map { activity ->
            ActivityData(
                id = activity.id ?: 0,
                customerId = activity.customerId,
                deviceId = activity.deviceId,
                activityType = activity.activityType,
                activityDescription = activity.activityDescription,
                metadata = activity.metadata,
                createdAt = activity.createdAt?.toString() ?: ""
            )
        }
        
        return ActivityListResponse(
            success = true,
            message = "Dealer activities retrieved successfully",
            activities = activityDataList,
            total = allActivities.totalElements,
            page = allActivities.number,
            pageSize = allActivities.size,
            totalPages = allActivities.totalPages
        )
    }
    
    // Admin method to get ALL activities from ALL dealers
    fun getAllActivitiesForAdmin(
        activityTypes: List<String> = emptyList(),
        dealerIds: List<String> = emptyList(),
        search: String? = null,
        page: Int = 0,
        size: Int = 50
    ): ActivityListResponse {
        val normalizedTypes = activityTypes.mapNotNull { it.trim().takeIf { s -> s.isNotBlank() } }.distinct()
        val normalizedDealerIds = dealerIds.mapNotNull { it.trim().takeIf { s -> s.isNotBlank() } }.distinct()
        val q = search?.trim()?.takeIf { it.isNotBlank() }?.lowercase()

        // Build pageable with descending order by createdAt
        val pageable: Pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))

        // Dealer filter: compute customerIds for selected dealers (used in spec)
        val dealerCustomerIds: Set<String> =
            if (normalizedDealerIds.isEmpty()) emptySet()
            else customerRepository.findByDealerIdIn(normalizedDealerIds).map { it.customerId }.toSet()

        // Search: compute customerIds matching customer name/id OR dealer name/id
        val searchCustomerIds: Set<String> =
            if (q == null) emptySet()
            else {
                val directCustomerIds = customerRepository.findCustomerIdsBySearch(q).toSet()
                val matchedDealerIds = dealerRepository.findDealerIdsBySearch(q).toSet()
                val dealerCustomerIdsForSearch =
                    if (matchedDealerIds.isEmpty()) emptySet()
                    else customerRepository.findByDealerIdIn(matchedDealerIds).map { it.customerId }.toSet()
                (directCustomerIds + dealerCustomerIdsForSearch).toSet()
            }

        var spec: Specification<Activity> = Specification { _, _, cb -> cb.conjunction() }
        activityTypesSpec(normalizedTypes)?.let { spec = spec.and(it) }
        dealerFilterSpec(normalizedDealerIds, dealerCustomerIds)?.let { spec = spec.and(it) }
        searchSpec(q, searchCustomerIds)?.let { spec = spec.and(it) }

        val pageResult = activityRepository.findAll(spec, pageable)

        // Enrich with customer + dealer names (for display + search UX)
        val customerIdsInPage = pageResult.content.mapNotNull { it.customerId }.distinct()
        val customersById =
            if (customerIdsInPage.isEmpty()) emptyMap()
            else customerRepository.findByCustomerIdIn(customerIdsInPage).associateBy { it.customerId }

        val dealerIdsInPage = customersById.values.map { it.dealerId }.distinct()
        val dealersById =
            if (dealerIdsInPage.isEmpty()) emptyMap()
            else dealerRepository.findByDealerIdIn(dealerIdsInPage).associateBy { it.dealerId }

        val activityDataList = pageResult.content.map { activity ->
            val customer = activity.customerId?.let { customersById[it] }
            val dealer = customer?.dealerId?.let { dealersById[it] }

            ActivityData(
                id = activity.id ?: 0,
                customerId = activity.customerId,
                customerName = customer?.name,
                dealerId = customer?.dealerId,
                dealerName = dealer?.name,
                deviceId = activity.deviceId,
                activityType = activity.activityType,
                activityDescription = activity.activityDescription,
                metadata = activity.metadata,
                createdAt = activity.createdAt?.toString() ?: ""
            )
        }

        return ActivityListResponse(
            success = true,
            message = "All activities retrieved successfully",
            activities = activityDataList,
            total = pageResult.totalElements,
            page = pageResult.number,
            pageSize = pageResult.size,
            totalPages = pageResult.totalPages
        )
    }

    fun getActivityTypesForAdmin(): ActivityTypesResponse {
        val types = activityRepository.findDistinctActivityTypes()
            .mapNotNull { it.trim().takeIf { s -> s.isNotBlank() } }
            .distinct()
        return ActivityTypesResponse(
            success = true,
            message = "Activity types retrieved successfully",
            activityTypes = types
        )
    }

    private fun activityTypesSpec(activityTypes: List<String>): Specification<Activity>? {
        if (activityTypes.isEmpty()) return null
        return Specification { root, _, _ ->
            root.get<String>("activityType").`in`(activityTypes)
        }
    }

    private fun dealerFilterSpec(
        dealerIds: List<String>,
        dealerCustomerIds: Set<String>
    ): Specification<Activity>? {
        if (dealerIds.isEmpty()) return null
        return Specification { root, _, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            // customerId IN customers of selected dealers
            if (dealerCustomerIds.isNotEmpty()) {
                predicates.add(root.get<String>("customerId").`in`(dealerCustomerIds))
            }

            // metadata contains "dealer_id":"<dealerId>" for dealer-related activities
            val metadataExpr = cb.lower(cb.coalesce(root.get("metadata"), ""))
            dealerIds.forEach { id ->
                val token = "%\"dealer_id\":\"${id.lowercase()}\"%"
                predicates.add(cb.like(metadataExpr, token))
            }

            cb.or(*predicates.toTypedArray())
        }
    }

    private fun searchSpec(q: String?, searchCustomerIds: Set<String>): Specification<Activity>? {
        if (q == null) return null
        return Specification { root, _, cb ->
            val likeQ = "%$q%"
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            predicates.add(cb.like(cb.lower(root.get("activityType")), likeQ))
            predicates.add(cb.like(cb.lower(cb.coalesce(root.get("activityDescription"), "")), likeQ))
            predicates.add(cb.like(cb.lower(cb.coalesce(root.get("customerId"), "")), likeQ))
            predicates.add(cb.like(cb.lower(cb.coalesce(root.get("deviceId"), "")), likeQ))
            predicates.add(cb.like(cb.lower(cb.coalesce(root.get("metadata"), "")), likeQ))

            if (searchCustomerIds.isNotEmpty()) {
                predicates.add(root.get<String>("customerId").`in`(searchCustomerIds))
            }

            cb.or(*predicates.toTypedArray())
        }
    }
}

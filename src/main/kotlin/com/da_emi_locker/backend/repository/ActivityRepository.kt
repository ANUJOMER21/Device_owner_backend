package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.Activity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ActivityRepository : JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {
    
    fun findByCustomerId(customerId: String): List<Activity>
    
    fun findByDeviceId(deviceId: String): List<Activity>
    
    fun findByActivityType(activityType: String): List<Activity>

    @Query("select distinct a.activityType from Activity a order by a.activityType")
    fun findDistinctActivityTypes(): List<String>
}

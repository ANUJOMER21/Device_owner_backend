package com.da_emi_locker.backend.repository

import com.da_emi_locker.backend.entity.ContactSubmission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContactSubmissionRepository : JpaRepository<ContactSubmission, Long> {
    // Basic CRUD operations provided by JpaRepository
}

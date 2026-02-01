package com.da_emi_locker.backend.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

/**
 * Database configuration class for PostgreSQL
 * 
 * This configuration enables:
 * - JPA repositories
 * - JPA auditing (for @CreatedDate, @LastModifiedDate, etc.)
 * - Transaction management
 */
@Configuration
@EnableJpaRepositories(basePackages = ["com.da_emi_locker.backend.repository"])
@EnableJpaAuditing
@EnableTransactionManagement
class DatabaseConfig

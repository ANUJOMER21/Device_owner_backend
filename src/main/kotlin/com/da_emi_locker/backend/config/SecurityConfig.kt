package com.da_emi_locker.backend.config

import com.da_emi_locker.backend.exception.GlobalExceptionHandler
import com.da_emi_locker.backend.security.JwtAuthenticationFilter
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Security configuration for the application
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper
) {
    
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder(10)
    }
    
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        // Use allowedOrigins (not allowedOriginPatterns) when allowCredentials is true
        configuration.allowedOrigins = listOf(
            "http://localhost:3000",
            "http://127.0.0.1:3000",
            "http://localhost:3001",
            "http://127.0.0.1:3001",
            // Vite dev server (admin-panel)
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            // Vite preview / alt ports
            "http://localhost:4173",
            "http://127.0.0.1:4173"
        )
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
        configuration.allowedHeaders = listOf("*")
        configuration.exposedHeaders = listOf("*")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L
        
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
    
    @Bean
    fun authenticationEntryPoint(): AuthenticationEntryPoint {
        return AuthenticationEntryPoint { request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException ->
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            
            val errorResponse = GlobalExceptionHandler.ErrorResponse(
                success = false,
                message = "Authentication required. Please provide a valid JWT token.",
                error = "UNAUTHORIZED",
                path = request.requestURI
            )
            
            objectMapper.writeValue(response.outputStream, errorResponse)
        }
    }
    
    @Bean
    fun accessDeniedHandler(): AccessDeniedHandler {
        return AccessDeniedHandler { request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException ->
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            
            val errorResponse = GlobalExceptionHandler.ErrorResponse(
                success = false,
                message = "Access denied. You don't have permission to access this resource.",
                error = "FORBIDDEN",
                path = request.requestURI
            )
            
            objectMapper.writeValue(response.outputStream, errorResponse)
        }
    }
    
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() } // Disable CSRF for API (enable in production with proper token handling)
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler())
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints - must come BEFORE more specific patterns
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Allow all OPTIONS requests for CORS preflight
                    .requestMatchers("/api/health/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/api/auth/login").permitAll()
                    .requestMatchers("/api/admin/login").permitAll() // Admin login endpoint - MUST come before /api/admin/**
                    .requestMatchers("/api/admin/health", "/api/admin/health/**").permitAll() // Admin health + S3 verification - MUST come before /api/admin/**
                    .requestMatchers("/api/dealers/register").permitAll() // Public registration
                    .requestMatchers("/api/qr-code/**").hasRole("DEALER") // Dealer QR + customer Device Owner QR require dealer auth
                    .requestMatchers("/api/contact").permitAll() // Contact form is public
                    .requestMatchers("/api/test/**").permitAll() // Test endpoints - remove in production
                    .requestMatchers("/uploads/**").permitAll() // locally stored uploaded images
                    .requestMatchers("/api/customer/device/**").permitAll() // Configure/customer app: activate, status, commands
                    .requestMatchers("/api/customer/dealer/**").permitAll() // Configure app: dealer details after activation
                    // Authenticated endpoints
                    .requestMatchers("/api/auth/set-pin", "/api/auth/verify-pin").authenticated()
                    .requestMatchers("/api/uploads/**").authenticated() // uploads require auth
                    // Role-based endpoints - MUST come AFTER public endpoints
                    .requestMatchers("/api/admin/**").hasRole("ADMIN") // Admin endpoints require ROLE_ADMIN (excludes /login and /health which are above)
                    .requestMatchers("/api/dealers/**", "/api/customers/**", "/api/dashboard/**", "/api/activities/**", "/api/devices/**", "/api/support/**", "/api/payments/**").hasRole("DEALER") // Dealer endpoints require ROLE_DEALER
                    .anyRequest().authenticated() // All other endpoints require authentication
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        
        return http.build()
    }
}

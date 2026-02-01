package com.da_emi_locker.backend.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Global exception handler for the application
 * Handles Spring Security exceptions and converts them to proper HTTP responses
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    
    /**
     * Handle AccessDeniedException - occurs when user is authenticated but lacks required role
     * Returns 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(
        ex: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Access denied for request: ${request.requestURI} - ${ex.message}")
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(
                success = false,
                message = "Access denied. You don't have permission to access this resource.",
                error = "FORBIDDEN",
                path = request.requestURI
            )
        )
    }
    
    /**
     * Handle AuthenticationException - occurs when user is not authenticated
     * Returns 401 Unauthorized
     */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(
        ex: AuthenticationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed for request: ${request.requestURI} - ${ex.message}")
        
        val message = when (ex) {
            is BadCredentialsException -> "Invalid credentials. Please check your email and password."
            is InsufficientAuthenticationException -> "Authentication required. Please provide a valid token."
            is AuthenticationCredentialsNotFoundException -> "No authentication credentials found. Please login first."
            else -> "Authentication failed. Please provide valid credentials."
        }
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(
                success = false,
                message = message,
                error = "UNAUTHORIZED",
                path = request.requestURI
            )
        )
    }
    
    /**
     * Handle validation errors from @Valid request body (MethodArgumentNotValidException)
     * Returns 400 Bad Request with field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ValidationErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { err ->
            err.field to (err.defaultMessage ?: "Invalid value")
        }
        logger.debug("Validation failed for request: ${request.requestURI} - $fieldErrors")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ValidationErrorResponse(
                success = false,
                message = "Validation failed",
                error = "BAD_REQUEST",
                path = request.requestURI,
                fieldErrors = fieldErrors
            )
        )
    }

    /**
     * Handle data constraint / integrity violations (e.g. unique, not null, FK).
     * Returns 400 so clients get a clear error instead of 500.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Data integrity violation for request: ${request.requestURI} - ${ex.message}")
        val message = ex.mostSpecificCause?.message?.take(200)
            ?: "Data constraint violation. Check that all required fields are valid and unique constraints are satisfied."
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                success = false,
                message = message,
                error = "BAD_REQUEST",
                path = request.requestURI
            )
        )
    }

    /**
     * Handle invalid enum/value errors (e.g. customer status 'inactive' after migration removed it).
     * Returns 400 with a hint to run migrations.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid argument for request: ${request.requestURI} - ${ex.message}")
        val hint = if (ex.message?.contains("enum", ignoreCase = true) == true ||
            ex.message?.contains("Unknown name value", ignoreCase = true) == true)
            " Ensure database migration V30 (customer status) has been applied."
        else
            ""
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                success = false,
                message = "Invalid data: ${ex.message?.take(150) ?: "bad value"}.$hint",
                error = "BAD_REQUEST",
                path = request.requestURI
            )
        )
    }

    /**
     * Handle generic exceptions
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error for request: ${request.requestURI}", ex)
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                success = false,
                message = "An unexpected error occurred. Please try again later.",
                error = "INTERNAL_SERVER_ERROR",
                path = request.requestURI
            )
        )
    }
    
    /**
     * Standard error response format
     */
    data class ErrorResponse(
        val success: Boolean,
        val message: String,
        val error: String,
        val path: String? = null
    )

    /**
     * Validation error response with field-level details
     */
    data class ValidationErrorResponse(
        val success: Boolean,
        val message: String,
        val error: String,
        val path: String? = null,
        val fieldErrors: Map<String, String> = emptyMap()
    )
}

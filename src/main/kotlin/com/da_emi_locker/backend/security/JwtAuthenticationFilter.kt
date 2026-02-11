package com.da_emi_locker.backend.security

import com.da_emi_locker.backend.entity.DealerStatus
import com.da_emi_locker.backend.entity.SalesExecutiveStatus
import com.da_emi_locker.backend.repository.DealerRepository
import com.da_emi_locker.backend.repository.SalesExecutiveRepository
import com.da_emi_locker.backend.service.JwtService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val dealerRepository: DealerRepository,
    private val salesExecutiveRepository: SalesExecutiveRepository,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {
    
    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        val requestUri = request.requestURI
        
        // Skip authentication filter for public endpoints
        if (isPublicEndpoint(requestUri)) {
            filterChain.doFilter(request, response)
            return
        }
        
        // If Authorization header is present, try to validate token
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)
            
            try {
                val isValid = jwtService.validateToken(token)
                
                if (isValid) {
                    val dealerId = jwtService.getDealerIdFromToken(token)
                    val role = jwtService.getRoleFromToken(token) ?: "dealer"
                    val salesExecutiveId = jwtService.getSalesExecutiveIdFromToken(token)
                    
                    if (dealerId != null && SecurityContextHolder.getContext().authentication == null) {

                        // ---------- Sales Executive session validation ----------
                        if (role == "sales_executive" && salesExecutiveId != null) {
                            val seOpt = salesExecutiveRepository.findBySalesExecutiveId(salesExecutiveId)
                            if (seOpt.isPresent) {
                                val se = seOpt.get()
                                if (!se.isLoggedIn) {
                                    log.warn("SE $salesExecutiveId token rejected: session ended")
                                    send401(response, "Session expired. Please login again.")
                                    return
                                }
                                val tokenJti = jwtService.getJtiFromToken(token)
                                if (se.currentTokenId.isNullOrBlank() || tokenJti != se.currentTokenId) {
                                    log.warn("SE $salesExecutiveId token rejected: jti mismatch")
                                    send401(response, "Session expired. Please login again.")
                                    return
                                }
                                if (se.status != SalesExecutiveStatus.active) {
                                    send401(response, "Account is ${se.status.name}. Please contact your dealer.")
                                    return
                                }
                            } else {
                                send401(response, "Sales executive not found.")
                                return
                            }

                            // SE gets ROLE_DEALER so existing dealer-scoped endpoints work
                            val authentication = UsernamePasswordAuthenticationToken(
                                dealerId,
                                null,
                                listOf(SimpleGrantedAuthority("ROLE_DEALER"))
                            )
                            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                            SecurityContextHolder.getContext().authentication = authentication

                            // Expose both dealerId and salesExecutiveId as request attributes
                            request.setAttribute("dealerId", dealerId)
                            request.setAttribute("salesExecutiveId", salesExecutiveId)
                            request.setAttribute("userRole", "sales_executive")
                            log.debug("SE $salesExecutiveId (dealer $dealerId) authenticated for: $requestUri")

                            filterChain.doFilter(request, response)
                            return
                        }

                        // ---------- Dealer session validation ----------
                        if (dealerId != "admin") {
                            val dealerOpt = dealerRepository.findByDealerId(dealerId)
                            if (dealerOpt.isPresent) {
                                val dealer = dealerOpt.get()
                                if (!dealer.isLoggedIn) {
                                    log.warn("Dealer $dealerId token rejected: session ended (is_logged_in=false)")
                                    send401(response, "Session expired. Please login again.")
                                    return
                                }
                                val currentTokenId = dealer.currentTokenId
                                if (currentTokenId.isNullOrBlank()) {
                                    log.warn("Dealer $dealerId token rejected: no active session")
                                    send401(response, "Session expired. Please login again.")
                                    return
                                }
                                val tokenJti = jwtService.getJtiFromToken(token)
                                if (tokenJti == null || tokenJti != currentTokenId) {
                                    log.warn("Dealer $dealerId token rejected: token does not match current session")
                                    send401(response, "Session expired. Please login again.")
                                    return
                                }
                                if (dealer.status != DealerStatus.active) {
                                    log.warn("Dealer $dealerId token rejected: account status is ${dealer.status}")
                                    send401(response, if (dealer.status == DealerStatus.suspended)
                                        "Account suspended. Please contact support." else "Account is inactive. Please contact support.")
                                    return
                                }
                            }
                        }
                        
                        // Determine role based on token subject:
                        // - "admin" token -> ROLE_ADMIN with adminId attribute
                        // - any other -> ROLE_DEALER with dealerId attribute
                        val (principal, authorities) =
                            if (dealerId == "admin") {
                                "admin" to listOf(SimpleGrantedAuthority("ROLE_ADMIN"))
                            } else {
                                dealerId to listOf(SimpleGrantedAuthority("ROLE_DEALER"))
                            }
                        
                        val authentication = UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            authorities
                        )
                        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                        
                        SecurityContextHolder.getContext().authentication = authentication
                        
                        if (dealerId == "admin") {
                            request.setAttribute("adminId", "admin")
                            log.debug("Admin authenticated for: $requestUri")
                        } else {
                            request.setAttribute("dealerId", dealerId)
                            request.setAttribute("userRole", "dealer")
                            log.debug("Dealer $dealerId authenticated for: $requestUri")
                        }
                    } else if (dealerId == null) {
                        log.warn("Token is valid but dealerId is null for request: $requestUri")
                    }
                } else {
                    log.warn("Invalid JWT token for request: $requestUri")
                }
            } catch (e: Exception) {
                log.error("Error processing JWT token for request: $requestUri", e)
            }
        }
        
        filterChain.doFilter(request, response)
    }
    
    /**
     * Check if the endpoint is public (doesn't require authentication)
     */
    private fun isPublicEndpoint(uri: String): Boolean {
        val publicPaths = listOf(
            "/api/health",
            "/actuator/health",
            "/api/auth/login",
            "/api/admin/login",
            "/api/admin/health",
            "/api/dealers/register",
            "/api/contact",
            "/api/test",
            "/uploads",
            "/api/customer/device"
        )
        
        return publicPaths.any { uri.startsWith(it) }
    }
    
    private fun send401(response: HttpServletResponse, message: String) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        val body = mapOf(
            "success" to false,
            "message" to message
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}

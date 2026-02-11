package com.da_emi_locker.backend.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService {
    
    private val logger = LoggerFactory.getLogger(JwtService::class.java)
    
    @Value("\${jwt.secret:your-256-bit-secret-key-change-this-in-production-minimum-32-characters}")
    private lateinit var secret: String
    
    @Value("\${jwt.expiration:86400000}") // 24 hours default
    private var expiration: Long = 86400000
    
    private fun getSigningKey(): SecretKey {
        val keyBytes = secret.toByteArray()
        // Ensure key is at least 32 bytes (256 bits) for HMAC-SHA256
        if (keyBytes.size < 32) {
            logger.warn("JWT secret is too short (${keyBytes.size} bytes). Minimum 32 bytes required. Padding with zeros.")
            val paddedKey = ByteArray(32)
            System.arraycopy(keyBytes, 0, paddedKey, 0, minOf(keyBytes.size, 32))
            return Keys.hmacShaKeyFor(paddedKey)
        }
        return Keys.hmacShaKeyFor(keyBytes)
    }
    
    /**
     * Generate a JWT with a unique token id (jti) for single-device login.
     * @param tokenId must be stored in dealer.currentTokenId; only this token will be accepted.
     * @param role "dealer", "sales_executive", or empty for admin
     * @param salesExecutiveId if role=sales_executive, the SE id
     */
    fun generateToken(
        dealerId: String,
        email: String,
        tokenId: String,
        role: String = "dealer",
        salesExecutiveId: String? = null
    ): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)
        
        logger.debug("Generating token for dealerId: $dealerId, role: $role, jti: $tokenId, expiry: $expiryDate")
        
        val builder = Jwts.builder()
            .subject(dealerId)
            .claim("email", email)
            .claim("dealerId", dealerId)
            .claim("jti", tokenId)
            .claim("role", role)
        
        salesExecutiveId?.let { builder.claim("salesExecutiveId", it) }
        
        return builder
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey())
            .compact()
    }
    
    fun validateToken(token: String): Boolean {
        return try {
            val claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
            logger.debug("Token validated successfully, subject: ${claims.payload.subject}")
            true
        } catch (e: Exception) {
            logger.warn("Token validation failed: ${e.message}")
            false
        }
    }
    
    fun getDealerIdFromToken(token: String): String? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .payload
            
            claims.get("dealerId", String::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun getEmailFromToken(token: String): String? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .payload
            
            claims.get("email", String::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /** Extract jti (token id) from JWT for single-device validation. */
    fun getJtiFromToken(token: String): String? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .payload
            
            claims.get("jti", String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /** Extract role from JWT: "dealer", "sales_executive", or null. */
    fun getRoleFromToken(token: String): String? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .payload
            claims.get("role", String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /** Extract salesExecutiveId from JWT (null for dealers/admin). */
    fun getSalesExecutiveIdFromToken(token: String): String? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .payload
            claims.get("salesExecutiveId", String::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

package com.togethertrip.gateway.global.security.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SecurityException
import org.springframework.stereotype.Component
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
) {

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    fun validateToken(token: String): Boolean {
        return try {
            parseClaims(token)
            true
        } catch (exception: ExpiredJwtException) {
            false
        } catch (exception: SecurityException) {
            false
        } catch (exception: MalformedJwtException) {
            false
        } catch (exception: IllegalArgumentException) {
            false
        }
    }

    fun getClaims(token: String): JwtClaims {
        val claims = parseClaims(token)

        return JwtClaims(
            userId = claims.subject.toLong(),
            role = UserRole.valueOf(claims["role"] as String),
            tokenType = TokenType.valueOf(claims["tokenType"] as String),
        )
    }

    private fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .requireIssuer(jwtProperties.issuer)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}

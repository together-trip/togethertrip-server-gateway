package com.togethertrip.gateway.global.security.jwt

data class JwtClaims(
    val userId: Long,
    val role: UserRole,
    val tokenType: TokenType,
)

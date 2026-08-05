package com.togethertrip.gateway.global.security

import com.togethertrip.gateway.global.security.jwt.UserRole

data class LocalTestIdentity(
    val userId: Long,
    val role: UserRole,
) {
    companion object {
        private const val TOKEN_PREFIX = "local-test:"

        fun parse(token: String): LocalTestIdentity? {
            if (!token.startsWith(TOKEN_PREFIX)) {
                return null
            }

            val parts = token.removePrefix(TOKEN_PREFIX).split(':')
            if (parts.size != 2) {
                return null
            }

            val role = when (parts[0]) {
                "user" -> UserRole.USER
                "admin" -> UserRole.ADMIN
                else -> return null
            }
            val userId = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
            return LocalTestIdentity(userId = userId, role = role)
        }
    }
}

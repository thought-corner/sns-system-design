package com.project.sns.auth.infrastructure

import com.project.sns.auth.application.LoginConcurrencyGuard
import com.project.sns.auth.application.SessionLimitExceededException
import jakarta.servlet.http.HttpServletRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.Locale
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class RedisLoginConcurrencyGuard(
    private val redisTemplate: StringRedisTemplate,
) : LoginConcurrencyGuard {
    override fun acquireUntilRequestCompletion(principalName: String, request: HttpServletRequest) {
        val lease = LoginLease(
            key = "$LOGIN_LOCK_NAMESPACE:${principalHash(principalName)}",
            token = UUID.randomUUID().toString(),
        )
        val acquired = redisTemplate.opsForValue().setIfAbsent(lease.key, lease.token, LOGIN_LOCK_TIMEOUT) == true
        if (!acquired) {
            throw SessionLimitExceededException()
        }
        request.setAttribute(LOGIN_LEASE_ATTRIBUTE, lease)
    }

    override fun release(request: HttpServletRequest) {
        val lease = request.getAttribute(LOGIN_LEASE_ATTRIBUTE) as? LoginLease ?: return
        request.removeAttribute(LOGIN_LEASE_ATTRIBUTE)

        try {
            redisTemplate.execute(RELEASE_SCRIPT, listOf(lease.key), lease.token)
        } catch (exception: RuntimeException) {
            logger.warn("Redis 로그인 잠금 해제에 실패했습니다. TTL 만료로 복구합니다.", exception)
        }
    }

    private fun principalHash(principalName: String): String {
        val normalizedPrincipal = principalName.trim().lowercase(Locale.ROOT)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalizedPrincipal.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

    private data class LoginLease(
        val key: String,
        val token: String,
    )

    private companion object {
        const val LOGIN_LOCK_NAMESPACE = "sns:session:login-lock"
        const val LOGIN_LEASE_ATTRIBUTE = "com.project.sns.LOGIN_CONCURRENCY_LEASE"
        val LOGIN_LOCK_TIMEOUT: Duration = Duration.ofMinutes(1)
        val RELEASE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
        val logger = LoggerFactory.getLogger(RedisLoginConcurrencyGuard::class.java)
    }
}

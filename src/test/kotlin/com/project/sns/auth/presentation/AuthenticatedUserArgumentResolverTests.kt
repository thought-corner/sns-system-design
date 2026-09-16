package com.project.sns.auth.presentation

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.ServletWebRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticatedUserArgumentResolverTests {
    private val resolver = AuthenticatedUserArgumentResolver()
    private val webRequest = ServletWebRequest(MockHttpServletRequest())

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `어노테이션이 붙은 AuthenticatedPrincipal 파라미터만 지원한다`() {
        assertTrue(resolver.supportsParameter(parameter(0)))
        assertFalse(resolver.supportsParameter(parameter(1)))
    }

    // 어노테이션은 붙었는데 타입이 다르면(Spring Security 의 동명 AuthenticatedPrincipal 을 잘못 import 한 경우 등) 조용히 빠지지 않고 즉시 실패한다.
    @Test
    fun `어노테이션이 붙었지만 타입이 다르면 즉시 실패한다`() {
        assertFailsWith<IllegalStateException> {
            resolver.supportsParameter(parameter(2))
        }
    }

    @Test
    fun `세션 주체의 username 을 사용자 ID 로 주입한다`() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("42", "n/a", "ROLE_USER")

        assertEquals(AuthenticatedPrincipal(42L), resolver.resolveArgument(parameter(0), null, webRequest, null))
    }

    @Test
    fun `인증 정보가 없거나 익명이면 거부한다`() {
        assertFailsWith<AuthenticationCredentialsNotFoundException> {
            resolver.resolveArgument(parameter(0), null, webRequest, null)
        }

        SecurityContextHolder.getContext().authentication =
            AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
        assertFailsWith<AuthenticationCredentialsNotFoundException> {
            resolver.resolveArgument(parameter(0), null, webRequest, null)
        }
    }

    @Test
    fun `username 이 사용자 ID 형식이 아닌 세션은 거부한다`() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("user@example.com", "n/a", "ROLE_USER")

        assertFailsWith<AuthenticationCredentialsNotFoundException> {
            resolver.resolveArgument(parameter(0), null, webRequest, null)
        }
    }

    @Suppress("UNUSED_PARAMETER", "unused")
    private fun handler(
        @AuthenticatedUser principal: AuthenticatedPrincipal,
        plainPrincipal: AuthenticatedPrincipal,
        @AuthenticatedUser id: Long,
    ) = Unit

    private fun parameter(index: Int) = MethodParameter(
        AuthenticatedUserArgumentResolverTests::class.java.getDeclaredMethod(
            "handler",
            AuthenticatedPrincipal::class.java,
            AuthenticatedPrincipal::class.java,
            Long::class.java,
        ),
        index,
    )
}

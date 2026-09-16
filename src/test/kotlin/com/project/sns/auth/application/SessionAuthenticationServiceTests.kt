@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.project.sns.auth.application

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.session.SessionAuthenticationException
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy
import org.springframework.security.web.context.HttpRequestResponseHolder
import org.springframework.security.web.context.SecurityContextRepository
import kotlin.test.assertSame
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SessionAuthenticationServiceTests {
    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `인증 결과를 세션 전략에 적용하고 SecurityContext에 저장한다`() {
        val authentication = UsernamePasswordAuthenticationToken.authenticated("1", null, emptyList())
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val sessionStrategy = RecordingSessionAuthenticationStrategy()
        val contextRepository = RecordingSecurityContextRepository()
        val concurrencyGuard = RecordingLoginConcurrencyGuard()
        val service = SessionAuthenticationService(contextRepository, sessionStrategy, concurrencyGuard)

        service.establish(authentication, request, response)

        assertSame(authentication, sessionStrategy.authentication)
        assertSame(authentication, contextRepository.securityContext?.authentication)
        assertSame(authentication, SecurityContextHolder.getContext().authentication)
        assertEquals(authentication.name, concurrencyGuard.principalName)
    }

    @Test
    fun `세션 전략의 최대 세션 초과를 애플리케이션 예외로 변환하고 컨텍스트를 저장하지 않는다`() {
        val authentication = UsernamePasswordAuthenticationToken.authenticated("1", null, emptyList())
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val contextRepository = RecordingSecurityContextRepository()
        val concurrencyGuard = RecordingLoginConcurrencyGuard()
        val sessionStrategy = SessionAuthenticationStrategy { _, _, _ ->
            throw SessionAuthenticationException("maximum sessions exceeded")
        }
        val service = SessionAuthenticationService(contextRepository, sessionStrategy, concurrencyGuard)

        val exception = assertFailsWith<SessionLimitExceededException> {
            service.establish(authentication, request, response)
        }

        assertEquals(AuthErrorCode.SESSION_LIMIT_EXCEEDED, exception.errorCode)
        assertEquals(SessionAuthenticationException::class, exception.cause!!::class)
        assertNull(contextRepository.securityContext)
        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(true, concurrencyGuard.released)
    }

    private class RecordingSessionAuthenticationStrategy : SessionAuthenticationStrategy {
        var authentication: Authentication? = null

        override fun onAuthentication(
            authentication: Authentication,
            request: HttpServletRequest,
            response: HttpServletResponse,
        ) {
            this.authentication = authentication
        }
    }

    private class RecordingSecurityContextRepository : SecurityContextRepository {
        var securityContext: SecurityContext? = null

        override fun saveContext(
            context: SecurityContext,
            request: HttpServletRequest,
            response: HttpServletResponse,
        ) {
            securityContext = context
        }

        override fun containsContext(request: HttpServletRequest): Boolean = securityContext != null

        override fun loadContext(requestResponseHolder: HttpRequestResponseHolder): SecurityContext =
            securityContext ?: SecurityContextHolder.createEmptyContext()
    }

    private class RecordingLoginConcurrencyGuard : LoginConcurrencyGuard {
        var principalName: String? = null
        var released: Boolean = false

        override fun acquireUntilRequestCompletion(principalName: String, request: HttpServletRequest) {
            this.principalName = principalName
        }

        override fun release(request: HttpServletRequest) {
            released = true
        }
    }
}

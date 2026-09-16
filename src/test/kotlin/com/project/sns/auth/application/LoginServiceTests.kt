package com.project.sns.auth.application

import com.project.sns.user.application.UserService
import com.project.sns.user.domain.User
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class LoginServiceTests {
    @Test
    fun `자격증명을 인증하고 인증 주체의 사용자를 반환한다`() {
        val authentication = UsernamePasswordAuthenticationToken.authenticated(EMAIL, null, emptyList())
        val authenticationManager = AuthenticationManager { request ->
            assertEquals(EMAIL, request.name)
            assertEquals(PASSWORD, request.credentials)
            authentication
        }
        val user = User(email = EMAIL, passwordHash = "encoded", nickname = "테스터", id = 1L)
        val userService = mock(UserService::class.java)
        doReturn(user).`when`(userService).getByEmail(EMAIL)

        val result = LoginService(userService, authenticationManager).login(EMAIL, PASSWORD)

        assertSame(authentication, result.authentication)
        assertSame(user, result.user)
    }

    @Test
    fun `인증 프레임워크의 실패를 애플리케이션 예외로 변환한다`() {
        val authenticationManager = AuthenticationManager {
            throw BadCredentialsException("invalid credentials")
        }
        val userService = mock(UserService::class.java)

        val exception = assertFailsWith<InvalidCredentialsException> {
            LoginService(userService, authenticationManager).login(EMAIL, PASSWORD)
        }

        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, exception.errorCode)
        assertEquals(BadCredentialsException::class, exception.cause!!::class)
    }

    companion object {
        private const val EMAIL = "user@example.com"
        private const val PASSWORD = "password123!"
    }
}

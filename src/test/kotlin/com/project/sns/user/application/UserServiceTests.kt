package com.project.sns.user.application

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import com.project.sns.user.domain.UserAlreadyExistsException
import com.project.sns.user.infrastructure.SpringDataUserJpaRepository
import com.project.sns.user.infrastructure.UserRepositoryAdapter
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@DataJpaTest
@Import(UserService::class, UserRepositoryAdapter::class, UserServiceTests.PasswordConfig::class)
class UserServiceTests {
    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var userRepository: SpringDataUserJpaRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun cleanUp() {
        userRepository.deleteAll()
    }

    @Test
    fun `회원가입 시 이메일을 정규화하고 비밀번호를 해싱한다`() {
        val user = userService.signUp(" User@Example.com ", PASSWORD, " 테스터 ")

        assertEquals("user@example.com", user.email)
        assertEquals("테스터", user.nickname)
        assertNotEquals(PASSWORD, user.passwordHash)
        assertTrue(passwordEncoder.matches(PASSWORD, user.passwordHash))
        assertEquals(user.id, userRepository.findByEmail("user@example.com")?.id)
    }

    @Test
    fun `이미 가입된 이메일은 대소문자와 공백이 달라도 거부한다`() {
        userService.signUp("user@example.com", PASSWORD, "테스터")

        assertFailsWith<UserAlreadyExistsException> {
            userService.signUp(" USER@example.com ", PASSWORD, "다른사용자")
        }
    }

    companion object {
        private const val PASSWORD = "password123!"
    }

    @TestConfiguration(proxyBeanMethods = false)
    class PasswordConfig {
        @Bean
        fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
    }
}

package com.project.sns.auth.presentation

import com.project.sns.TestSessionConfig
import com.project.sns.user.infrastructure.SpringDataUserJpaRepository
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSessionConfig::class)
abstract class AuthApiTestSupport {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var userRepository: SpringDataUserJpaRepository

    @Autowired
    private lateinit var sessionRegistry: SessionRegistry

    @BeforeEach
    fun cleanUpAuthFixtures() {
        userRepository.deleteAll()
        sessionRegistry.allPrincipals
            .flatMap { principal -> sessionRegistry.getAllSessions(principal, true) }
            .forEach { session -> sessionRegistry.removeSessionInformation(session.sessionId) }
    }

    protected fun signUp(): ResultActions = mockMvc.perform(
        post("/api/auth/signup")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(signUpJson()),
    )

    protected fun login(password: String = PASSWORD): ResultActions = mockMvc.perform(
        post("/api/auth/login")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginJson(password)),
    )

    protected fun signUpJson(email: String = EMAIL) =
        """{"email":"$email","password":"$PASSWORD","nickname":"테스터"}"""

    private fun loginJson(password: String) =
        """{"email":"$EMAIL","password":"$password"}"""

    protected companion object {
        const val EMAIL = "user@example.com"
        const val PASSWORD = "password123!"
    }
}

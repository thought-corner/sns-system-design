package com.project.sns

import com.project.sns.auth.application.LoginConcurrencyGuard
import jakarta.servlet.http.HttpServletRequest
import java.util.concurrent.ConcurrentHashMap
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.session.MapSessionRepository
import org.springframework.session.Session

@TestConfiguration(proxyBeanMethods = false)
class TestSessionConfig {
    @Bean
    fun sessionRepository() = MapSessionRepository(ConcurrentHashMap<String, Session>())

    @Bean
    fun sessionRegistry(): SessionRegistry = SessionRegistryImpl()

    @Bean
    fun loginConcurrencyGuard(): LoginConcurrencyGuard = object : LoginConcurrencyGuard {
        override fun acquireUntilRequestCompletion(principalName: String, request: HttpServletRequest) = Unit

        override fun release(request: HttpServletRequest) = Unit
    }
}

package com.project.sns.auth.application

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.session.SessionAuthenticationException
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Service

@Service
class SessionAuthenticationService(
    private val securityContextRepository: SecurityContextRepository,
    private val sessionAuthenticationStrategy: SessionAuthenticationStrategy,
    private val loginConcurrencyGuard: LoginConcurrencyGuard,
) {
    fun establish(
        authentication: Authentication,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        loginConcurrencyGuard.acquireUntilRequestCompletion(authentication.name, request)

        try {
            sessionAuthenticationStrategy.onAuthentication(authentication, request, response)
        } catch (exception: SessionAuthenticationException) {
            loginConcurrencyGuard.release(request)
            throw SessionLimitExceededException(exception)
        } catch (exception: RuntimeException) {
            loginConcurrencyGuard.release(request)
            throw exception
        }

        try {
            val securityContext = SecurityContextHolder.createEmptyContext()
            securityContext.authentication = authentication
            SecurityContextHolder.setContext(securityContext)
            securityContextRepository.saveContext(securityContext, request, response)
        } catch (exception: RuntimeException) {
            loginConcurrencyGuard.release(request)
            throw exception
        }
    }
}

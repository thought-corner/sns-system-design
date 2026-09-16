package com.project.sns.auth.infrastructure

import com.project.sns.auth.application.LoginConcurrencyGuard
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.session.web.http.SessionRepositoryFilter
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(SessionRepositoryFilter.DEFAULT_ORDER - 1)
class LoginConcurrencyGuardReleaseFilter(
    private val loginConcurrencyGuard: LoginConcurrencyGuard,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            filterChain.doFilter(request, response)
        } finally {
            loginConcurrencyGuard.release(request)
        }
    }
}

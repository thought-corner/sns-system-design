package com.project.sns.auth.presentation

import com.project.sns.auth.application.userIdOrNull
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * `@AuthenticatedUser principal: AuthenticatedPrincipal` 에 `SecurityContextHolder` 의 주체 ID 를 주입한다. DB 를 건드리지 않는다.
 * 인증 정보가 없거나 익명이거나 principal 이 ID 형식이 아니면(이 규칙 이전 세션) fail-closed — `AuthenticationException` 을 던져 `ExceptionTranslationFilter` 가 401 로 끝낸다(`SecurityConfig.authenticated()` 가 먼저 막으므로 보통 도달하지 않는 방어선).
 */
@Component
class AuthenticatedUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        if (!parameter.hasParameterAnnotation(AuthenticatedUser::class.java)) {
            return false
        }
        // Spring Security 에도 같은 단순명의 AuthenticatedPrincipal 인터페이스가 있다 — 자동 import 가 그쪽을 고르면 이 리졸버가 빠지고
        // 기본 리졸버가 인터페이스를 인스턴스화하려다 매 요청 500 이 된다. 첫 요청에서 원인이 드러나도록 여기서 끊는다.
        check(AuthenticatedPrincipal::class.java.isAssignableFrom(parameter.parameterType)) {
            "@AuthenticatedUser 는 ${AuthenticatedPrincipal::class.java.name} 타입에만 붙일 수 있습니다: ${parameter.parameterType.name}"
        }
        return true
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthenticatedPrincipal {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            throw AuthenticationCredentialsNotFoundException("인증된 세션이 없습니다.")
        }
        val userId = authentication.userIdOrNull()
            ?: throw AuthenticationCredentialsNotFoundException("세션 주체를 식별할 수 없습니다. 다시 로그인해야 합니다.")
        return AuthenticatedPrincipal(userId)
    }
}

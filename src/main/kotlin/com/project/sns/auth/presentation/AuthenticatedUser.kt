package com.project.sns.auth.presentation

/**
 * 현재 세션 주체를 `AuthenticatedPrincipal` 로 받는다(`AuthenticatedUserArgumentResolver`).
 * 행위자는 항상 세션에서 오고 요청 본문·경로의 사용자 ID 를 행위자로 믿지 않는다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthenticatedUser

/**
 * 세션 주체의 식별자. 인증 컨텍스트(`Authentication.name` = `users.id`)에서 꺼낸 값만 담는 presentation 경계 객체이며
 * 도메인 엔티티가 아니다 — 사용자 조회는 필요한 서비스가 트랜잭션 안에서 한다.
 */
data class AuthenticatedPrincipal(
    val id: Long,
)

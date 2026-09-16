package com.project.sns.auth.application

import org.springframework.security.core.Authentication

/**
 * Spring Security principal(`Authentication.name`)은 `users.id` 의 10진 문자열이다(`UserService.loadUserByUsername`).
 * 이메일이 아니므로 세션 인덱스·잠금 키·로그에 PII 가 남지 않고, 행위자를 요청마다 조회하지 않는다.
 * 숫자가 아니면 이 규칙 이전에 만들어진 세션 등 신뢰할 수 없는 주체다 — 호출자가 미인증으로 다룬다.
 */
fun Authentication.userIdOrNull(): Long? = name?.toLongOrNull()

package com.project.sns.auth.presentation

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionSecurityTests : AuthApiTestSupport() {
    @Test
    fun `로그인 세션으로 사용자를 조회하고 로그아웃한다`() {
        signUp().andExpect(status().isCreated)
        val loginResult = login().andExpect(status().isOk).andReturn()
        val session = loginResult.request.getSession(false) as MockHttpSession

        mockMvc.perform(get("/api/auth/session").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(EMAIL))

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent)

        assertTrue(session.isInvalid)
    }

    @Test
    fun `인증 없이 세션 정보를 조회할 수 없다`() {
        mockMvc.perform(get("/api/auth/session"))
            .andExpect(status().isUnauthorized)
    }

    // principal 이 users.id 가 아닌 세션(이 규칙 이전에 만들어진 이메일 principal)은 인가는 통과하지만 주체 주입에서 401 로 거부된다.
    @Test
    fun `principal 이 사용자 ID 가 아닌 세션은 401 로 거부된다`() {
        mockMvc.perform(get("/api/auth/session").with(user(EMAIL)))
            .andExpect(status().isUnauthorized)
    }

    // 세션은 유효한데 users 행이 없으면(수동 삭제 등) 404 가 아니라 resolver 와 같은 401 — 세션 주체를 식별할 수 없는 상태는 모두 fail-closed.
    @Test
    fun `사용자 행이 없는 세션은 401 로 거부된다`() {
        mockMvc.perform(get("/api/auth/session").with(user("9223372036854775807")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `CSRF 토큰 없이 상태를 변경할 수 없다`() {
        mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpJson()),
        )
            .andExpect(status().isForbidden)

        assertFalse(userRepository.existsByEmail(EMAIL))
    }
}

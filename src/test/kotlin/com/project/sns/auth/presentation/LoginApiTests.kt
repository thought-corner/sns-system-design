package com.project.sns.auth.presentation

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class LoginApiTests : AuthApiTestSupport() {
    @Test
    fun `올바른 자격증명으로 로그인한다`() {
        signUp().andExpect(status().isCreated)

        login()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(jsonPath("$.nickname").value("테스터"))
    }

    @Test
    fun `잘못된 비밀번호는 인증 실패 응답을 반환한다`() {
        signUp().andExpect(status().isCreated)

        login(password = "wrong-password")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
    }
}

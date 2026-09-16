package com.project.sns.auth.presentation

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ConcurrentSessionTests : AuthApiTestSupport() {
    @Test
    fun `이미 로그인한 사용자의 두 번째 로그인을 거부한다`() {
        signUp().andExpect(status().isCreated)
        login().andExpect(status().isOk)

        login()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SESSION_LIMIT_EXCEEDED"))
    }
}

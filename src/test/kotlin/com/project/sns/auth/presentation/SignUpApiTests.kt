package com.project.sns.auth.presentation

import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SignUpApiTests : AuthApiTestSupport() {
    @Test
    fun `올바른 회원가입 요청은 생성된 사용자를 반환한다`() {
        mockMvc.perform(
            post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpJson(email = "User@Example.com")),
        )
            .andExpect(status().isCreated)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.nickname").value("테스터"))
            .andExpect(jsonPath("$.createdAt", matchesPattern(".+Z")))
    }

    @Test
    fun `중복 이메일은 충돌 응답을 반환한다`() {
        signUp().andExpect(status().isCreated)

        signUp()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"))
    }

    @Test
    fun `올바르지 않은 입력은 필드 오류를 반환한다`() {
        mockMvc.perform(
            post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"invalid","password":"short","nickname":""}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.fieldErrors.email").exists())
            .andExpect(jsonPath("$.fieldErrors.password").exists())
            .andExpect(jsonPath("$.fieldErrors.nickname").exists())
    }
}

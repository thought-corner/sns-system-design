package com.project.sns.auth.presentation

import com.project.sns.user.domain.User
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class SignUpRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 72)
    val password: String,

    @field:NotBlank
    @field:Size(min = 2, max = 30)
    val nickname: String,
)

data class LoginRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 320)
    val email: String,

    @field:NotBlank
    @field:Size(max = 72)
    val password: String,
)

data class UserResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = requireNotNull(user.id),
            email = user.email,
            nickname = user.nickname,
            createdAt = user.createdAt,
        )
    }
}

data class CsrfTokenResponse(
    val headerName: String,
    val parameterName: String,
    val token: String,
)

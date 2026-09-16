package com.project.sns.auth.presentation

import com.project.sns.auth.application.LoginService
import com.project.sns.auth.application.SessionAuthenticationService
import com.project.sns.user.application.UserService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userService: UserService,
    private val loginService: LoginService,
    private val sessionAuthenticationService: SessionAuthenticationService,
) {
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody request: SignUpRequest): UserResponse =
        UserResponse.from(userService.signUp(request.email, request.password, request.nickname))

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody loginRequest: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): UserResponse {
        val loginResult = loginService.login(loginRequest.email, loginRequest.password)
        sessionAuthenticationService.establish(loginResult.authentication, request, response)
        return UserResponse.from(loginResult.user)
    }

    @GetMapping("/session")
    fun session(authentication: Authentication): UserResponse =
        UserResponse.from(userService.getByEmail(authentication.name))

    @GetMapping("/csrf")
    fun csrf(csrfToken: CsrfToken): CsrfTokenResponse = CsrfTokenResponse(
        headerName = csrfToken.headerName,
        parameterName = csrfToken.parameterName,
        token = csrfToken.token,
    )
}

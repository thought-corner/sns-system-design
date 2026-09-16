package com.project.sns.auth.presentation

import com.project.sns.auth.application.LoginService
import com.project.sns.auth.application.SessionAuthenticationService
import com.project.sns.user.application.UserService
import com.project.sns.user.domain.UserNotFoundException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
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
    fun session(@AuthenticatedUser principal: AuthenticatedPrincipal): UserResponse {
        // 세션은 살아 있는데 users 행이 없으면 주체를 식별할 수 없는 세션이다 — 404 가 아니라 resolver 와 같은 401(fail-closed)로 재로그인을 유도한다.
        // cause 로 UserNotFoundException 을 넘기지 않는다: Spring MVC 는 @ExceptionHandler 를 cause 체인까지 뒤져 ApplicationException 핸들러(404)로 보내 버린다.
        val user = try {
            userService.getById(principal.id)
        } catch (_: UserNotFoundException) {
            throw AuthenticationCredentialsNotFoundException("세션 주체에 해당하는 사용자가 없습니다. 다시 로그인해야 합니다.")
        }
        return UserResponse.from(user)
    }

    @GetMapping("/csrf")
    fun csrf(csrfToken: CsrfToken): CsrfTokenResponse = CsrfTokenResponse(
        headerName = csrfToken.headerName,
        parameterName = csrfToken.parameterName,
        token = csrfToken.token,
    )
}

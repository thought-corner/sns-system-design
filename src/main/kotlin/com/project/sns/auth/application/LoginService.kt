package com.project.sns.auth.application

import com.project.sns.user.application.UserService
import com.project.sns.user.domain.User
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.stereotype.Service

@Service
class LoginService(
    private val userService: UserService,
    private val authenticationManager: AuthenticationManager,
) {
    fun login(email: String, password: String): LoginResult {
        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password),
            )
        } catch (exception: AuthenticationException) {
            throw InvalidCredentialsException(exception)
        }
        return LoginResult(
            authentication = authentication,
            user = userService.getByEmail(authentication.name),
        )
    }
}

data class LoginResult(
    val authentication: Authentication,
    val user: User,
)

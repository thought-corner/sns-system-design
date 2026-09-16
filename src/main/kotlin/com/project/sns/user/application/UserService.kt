package com.project.sns.user.application

import com.project.sns.user.domain.User
import com.project.sns.user.domain.UserAlreadyExistsException
import com.project.sns.user.domain.UserRepository
import java.util.Locale
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : UserDetailsService {
    @Transactional
    fun signUp(email: String, password: String, nickname: String): User {
        val normalizedEmail = normalizeEmail(email)
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw UserAlreadyExistsException()
        }

        return try {
            userRepository.save(
                User(
                    email = normalizedEmail,
                    passwordHash = requireNotNull(passwordEncoder.encode(password)),
                    nickname = nickname.trim(),
                ),
            )
        } catch (exception: DataIntegrityViolationException) {
            throw UserAlreadyExistsException(exception)
        }
    }

    @Transactional(readOnly = true)
    fun getByEmail(email: String): User = userRepository.findByEmail(normalizeEmail(email))
        ?: throw UsernameNotFoundException("사용자를 찾을 수 없습니다.")

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val user = getByEmail(username)
        return org.springframework.security.core.userdetails.User
            .withUsername(user.email)
            .password(user.passwordHash)
            .roles("USER")
            .build()
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)
}

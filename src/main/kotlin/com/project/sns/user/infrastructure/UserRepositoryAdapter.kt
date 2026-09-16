package com.project.sns.user.infrastructure

import com.project.sns.user.domain.User
import com.project.sns.user.domain.UserRepository
import org.springframework.stereotype.Repository

@Repository
class UserRepositoryAdapter(
    private val jpaRepository: SpringDataUserJpaRepository,
) : UserRepository {
    override fun findByEmail(email: String): User? = jpaRepository.findByEmail(email)

    override fun existsByEmail(email: String): Boolean = jpaRepository.existsByEmail(email)

    override fun save(user: User): User = jpaRepository.saveAndFlush(user)
}

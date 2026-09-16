package com.project.sns.user.infrastructure

import com.project.sns.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataUserJpaRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean
}

package com.project.sns.user.domain

interface UserRepository {
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    fun save(user: User): User
}

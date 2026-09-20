package com.project.sns.post.infrastructure

import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostRepository
import org.springframework.stereotype.Repository

@Repository
class PostRepositoryAdapter(
    private val jpaRepository: SpringDataPostJpaRepository,
) : PostRepository {
    override fun save(post: Post): Post = jpaRepository.save(post)

    override fun findActiveById(id: Long): Post? = jpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findActiveByIdForShare(id: Long): Post? = jpaRepository.findActiveByIdForShare(id)

    override fun findById(id: Long): Post? = jpaRepository.findById(id).orElse(null)

    override fun softDelete(postId: Long): Boolean = jpaRepository.softDeleteById(postId) == 1

    override fun createRepost(authorId: Long, originalId: Long): Boolean =
        jpaRepository.insertRepostIfAbsent(authorId, originalId) == 1

    override fun softDeleteRepost(authorId: Long, originalId: Long): Boolean =
        jpaRepository.softDeleteRepost(authorId, originalId) == 1

    override fun softDeleteRepostsOf(originalId: Long): Int = jpaRepository.softDeleteRepostsOf(originalId)
}

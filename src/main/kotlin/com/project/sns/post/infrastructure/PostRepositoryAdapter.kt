package com.project.sns.post.infrastructure

import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class PostRepositoryAdapter(
    private val jpaRepository: SpringDataPostJpaRepository,
    private val countsRepository: SpringDataPostCountsJpaRepository,
) : PostRepository {
    override fun save(post: Post): Post = jpaRepository.save(post)

    override fun findActiveById(id: Long): Post? = jpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findActiveByIdForShare(id: Long): Post? = jpaRepository.findActiveByIdForShare(id)

    override fun findById(id: Long): Post? = jpaRepository.findById(id).orElse(null)

    override fun softDelete(postId: Long): Boolean = jpaRepository.softDeleteById(postId) == 1

    override fun getCounts(postId: Long): PostCounts =
        countsRepository.findById(postId).orElseGet { PostCounts(postId = postId) }

    override fun increaseCounts(postId: Long, delta: PostCountDelta) {
        countsRepository.addCounts(postId, delta.reply, delta.quote, delta.repost, delta.like, delta.view)
    }

    override fun decreaseCounts(postId: Long, delta: PostCountDelta) {
        val affected = countsRepository.subtractCounts(postId, delta.reply, delta.quote, delta.repost, delta.like, delta.view)
        if (affected == 0) {
            logger.warn("post_counts 감소 실패: postId={} delta={}", postId, delta)
        }
    }

    override fun createRepost(authorId: Long, originalId: Long): Boolean =
        jpaRepository.insertRepostIfAbsent(authorId, originalId) == 1

    override fun softDeleteRepost(authorId: Long, originalId: Long): Boolean =
        jpaRepository.softDeleteRepost(authorId, originalId) == 1

    override fun softDeleteRepostsOf(originalId: Long): Int = jpaRepository.softDeleteRepostsOf(originalId)

    private companion object {
        val logger = LoggerFactory.getLogger(PostRepositoryAdapter::class.java)
    }
}

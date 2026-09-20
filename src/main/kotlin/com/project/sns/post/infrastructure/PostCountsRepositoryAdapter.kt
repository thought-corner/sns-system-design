package com.project.sns.post.infrastructure

import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostCountsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class PostCountsRepositoryAdapter(
    private val countsRepository: SpringDataPostCountsJpaRepository,
) : PostCountsRepository {
    override fun get(postId: Long): PostCounts =
        countsRepository.findById(postId).orElseGet { PostCounts(postId = postId) }

    override fun increase(postId: Long, delta: PostCountDelta) {
        countsRepository.addCounts(postId, delta.reply, delta.quote, delta.repost, delta.like, delta.view)
    }

    override fun decrease(postId: Long, delta: PostCountDelta) {
        val affected =
            countsRepository.subtractCounts(postId, delta.reply, delta.quote, delta.repost, delta.like, delta.view)
        if (affected == 0) {
            logger.warn("post_counts 감소 실패: postId={} delta={}", postId, delta)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(PostCountsRepositoryAdapter::class.java)
    }
}

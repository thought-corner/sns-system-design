package com.project.sns.post.application

import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostCountsRepository
import com.project.sns.post.domain.PostRepository
import com.project.sns.timeline.application.PostReadService
import com.project.sns.timeline.application.TimelineFanoutPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RepostService(
    private val postTargetResolver: PostTargetResolver,
    private val postRepository: PostRepository,
    private val postCountsRepository: PostCountsRepository,
    private val timelineFanoutPublisher: TimelineFanoutPublisher,
    private val postReadService: PostReadService,
) {
    @Transactional
    fun repost(userId: Long, postId: Long): PostChangeResult {
        val originalId = requireNotNull(postTargetResolver.resolveTarget(postId).id)
        val changed = postRepository.createRepost(userId, originalId)
        if (changed) {
            postCountsRepository.increase(originalId, PostCountDelta.REPOST)
            postRepository.findActiveRepostId(userId, originalId)
                ?.let { timelineFanoutPublisher.publishAfterCommit(it, userId) }
        }
        return PostChangeResult(changed = changed)
    }

    @Transactional
    fun undoRepost(userId: Long, postId: Long): PostChangeResult {
        val originalId = requireNotNull(postTargetResolver.resolveTarget(postId).id)
        val repostRowId = postRepository.findActiveRepostId(userId, originalId)
        val changed = postRepository.softDeleteRepost(userId, originalId)
        if (changed) {
            postCountsRepository.decrease(originalId, PostCountDelta.REPOST)
            repostRowId?.let { postReadService.evictAfterCommit(it) }
        }
        return PostChangeResult(changed = changed)
    }
}

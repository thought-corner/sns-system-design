package com.project.sns.post.application

import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostLikeRepository
import com.project.sns.post.domain.PostCountsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val postTargetResolver: PostTargetResolver,
    private val postCountsRepository: PostCountsRepository,
    private val postLikeRepository: PostLikeRepository,
) {
    @Transactional
    fun like(userId: Long, postId: Long): PostChangeResult {
        val targetId = requireNotNull(postTargetResolver.resolveTarget(postId).id)
        val changed = postLikeRepository.create(userId, targetId)
        if (changed) {
            postCountsRepository.increase(targetId, PostCountDelta.LIKE)
        }
        return PostChangeResult(changed = changed)
    }

    @Transactional
    fun unlike(userId: Long, postId: Long): PostChangeResult {
        val targetId = requireNotNull(postTargetResolver.resolveTarget(postId).id)
        val changed = postLikeRepository.softDelete(userId, targetId)
        if (changed) {
            postCountsRepository.decrease(targetId, PostCountDelta.LIKE)
        }
        return PostChangeResult(changed = changed)
    }
}

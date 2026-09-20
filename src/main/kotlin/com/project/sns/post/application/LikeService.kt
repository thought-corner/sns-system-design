package com.project.sns.post.application

import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostLikeRepository
import com.project.sns.post.domain.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val postService: PostService,
    private val postRepository: PostRepository,
    private val postLikeRepository: PostLikeRepository,
) {
    @Transactional
    fun like(userId: Long, postId: Long): PostChangeResult {
        val targetId = requireNotNull(postService.resolveTarget(postId).id)
        val changed = postLikeRepository.create(userId, targetId)
        if (changed) {
            postRepository.increaseCounts(targetId, PostCountDelta.LIKE)
        }
        return PostChangeResult(changed = changed)
    }

    @Transactional
    fun unlike(userId: Long, postId: Long): PostChangeResult {
        val targetId = requireNotNull(postService.resolveTarget(postId).id)
        val changed = postLikeRepository.softDelete(userId, targetId)
        if (changed) {
            postRepository.decreaseCounts(targetId, PostCountDelta.LIKE)
        }
        return PostChangeResult(changed = changed)
    }
}

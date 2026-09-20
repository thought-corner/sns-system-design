package com.project.sns.post.application

import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RepostService(
    private val postService: PostService,
    private val postRepository: PostRepository,
) {
    @Transactional
    fun repost(userId: Long, postId: Long): PostChangeResult {
        val originalId = requireNotNull(postService.resolveTarget(postId).id)
        val changed = postRepository.createRepost(userId, originalId)
        if (changed) {
            postRepository.increaseCounts(originalId, PostCountDelta.REPOST)
        }
        return PostChangeResult(changed = changed)
    }

    @Transactional
    fun undoRepost(userId: Long, postId: Long): PostChangeResult {
        val originalId = requireNotNull(postService.resolveTarget(postId).id)
        val changed = postRepository.softDeleteRepost(userId, originalId)
        if (changed) {
            postRepository.decreaseCounts(originalId, PostCountDelta.REPOST)
        }
        return PostChangeResult(changed = changed)
    }
}

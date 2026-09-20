package com.project.sns.post.application

import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostRepository
import com.project.sns.post.domain.PostViewRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ViewService(
    private val postService: PostService,
    private val postRepository: PostRepository,
    private val postViewRepository: PostViewRepository,
) {
    @Transactional
    fun view(userId: Long, postId: Long): PostChangeResult {
        val targetId = requireNotNull(postService.resolveTarget(postId).id)
        val changed = postViewRepository.record(userId, targetId)
        if (changed) {
            postRepository.increaseCounts(targetId, PostCountDelta.VIEW)
        }
        return PostChangeResult(changed = changed)
    }
}

package com.project.sns.post.application

import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostCountsRepository
import com.project.sns.post.domain.PostViewRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ViewService(
    private val postTargetResolver: PostTargetResolver,
    private val postCountsRepository: PostCountsRepository,
    private val postViewRepository: PostViewRepository,
) {
    @Transactional
    fun view(userId: Long, postId: Long): PostChangeResult {
        val targetId = requireNotNull(postTargetResolver.resolveTarget(postId).id)
        val changed = postViewRepository.record(userId, targetId)
        if (changed) {
            postCountsRepository.increase(targetId, PostCountDelta.VIEW)
        }
        return PostChangeResult(changed = changed)
    }
}

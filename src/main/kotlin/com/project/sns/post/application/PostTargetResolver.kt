package com.project.sns.post.application

import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostNotFoundException
import com.project.sns.post.domain.PostRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PostTargetResolver(
    private val postRepository: PostRepository,
) {
    @Transactional(readOnly = true)
    fun getActive(postId: Long): Post = postRepository.findActiveById(postId) ?: throw PostNotFoundException()

    @Transactional
    fun resolveTarget(postId: Long): Post {
        val post = getActive(postId)
        val targetId = post.repostOfId ?: requireNotNull(post.id)
        return postRepository.findActiveByIdForShare(targetId) ?: throw PostNotFoundException()
    }
}

package com.project.sns.post.application

import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReplyService(
    private val postService: PostService,
    private val postRepository: PostRepository,
) {
    @Transactional
    fun reply(authorId: Long, parentPostId: Long, content: String): PostDetail {
        val parentId = requireNotNull(postService.resolveTarget(parentPostId).id)
        val reply = postRepository.save(Post(authorId = authorId, content = content, parentPostId = parentId))
        postRepository.increaseCounts(parentId, PostCountDelta.REPLY)
        return PostDetail.of(reply, PostCounts(postId = requireNotNull(reply.id)))
    }
}

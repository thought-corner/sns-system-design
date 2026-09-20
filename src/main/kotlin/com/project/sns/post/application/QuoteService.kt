package com.project.sns.post.application

import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuoteService(
    private val postService: PostService,
    private val postRepository: PostRepository,
) {
    @Transactional
    fun quote(authorId: Long, quotedPostId: Long, content: String): PostDetail {
        val quotedId = requireNotNull(postService.resolveTarget(quotedPostId).id)
        val quote = postRepository.save(Post(authorId = authorId, content = content, quotedPostId = quotedId))
        postRepository.increaseCounts(quotedId, PostCountDelta.QUOTE)
        return PostDetail.of(quote, PostCounts(postId = requireNotNull(quote.id)))
    }
}

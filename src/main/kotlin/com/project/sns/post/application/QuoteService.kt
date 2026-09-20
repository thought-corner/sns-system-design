package com.project.sns.post.application

import com.project.sns.media.application.MediaAttachmentService
import com.project.sns.media.application.MediaViewService
import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostCountsRepository
import com.project.sns.post.domain.PostRepository
import com.project.sns.timeline.application.TimelineFanoutPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuoteService(
    private val postTargetResolver: PostTargetResolver,
    private val postRepository: PostRepository,
    private val postCountsRepository: PostCountsRepository,
    private val mediaAttachmentService: MediaAttachmentService,
    private val mediaViewService: MediaViewService,
    private val timelineFanoutPublisher: TimelineFanoutPublisher,
) {
    @Transactional
    fun quote(authorId: Long, quotedPostId: Long, content: String, mediaIds: List<Long> = emptyList()): PostDetail {
        val quotedId = requireNotNull(postTargetResolver.resolveTarget(quotedPostId).id)
        val quote = postRepository.save(Post(authorId = authorId, content = content, quotedPostId = quotedId))
        val quoteId = requireNotNull(quote.id)
        postCountsRepository.increase(quotedId, PostCountDelta.QUOTE)
        mediaAttachmentService.attach(authorId, quoteId, mediaIds)
        timelineFanoutPublisher.publishAfterCommit(quoteId, authorId)
        return PostDetail.of(quote, PostCounts(postId = quoteId), mediaViewService.listForPost(quoteId))
    }
}

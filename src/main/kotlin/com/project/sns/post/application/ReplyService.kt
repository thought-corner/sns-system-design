package com.project.sns.post.application

import com.project.sns.media.application.MediaAttachmentService
import com.project.sns.media.application.MediaViewService
import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostCountsRepository
import com.project.sns.post.domain.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReplyService(
    private val postTargetResolver: PostTargetResolver,
    private val postRepository: PostRepository,
    private val postCountsRepository: PostCountsRepository,
    private val mediaAttachmentService: MediaAttachmentService,
    private val mediaViewService: MediaViewService,
) {
    @Transactional
    fun reply(authorId: Long, parentPostId: Long, content: String, mediaIds: List<Long> = emptyList()): PostDetail {
        val parentId = requireNotNull(postTargetResolver.resolveTarget(parentPostId).id)
        val reply = postRepository.save(Post(authorId = authorId, content = content, parentPostId = parentId))
        val replyId = requireNotNull(reply.id)
        postCountsRepository.increase(parentId, PostCountDelta.REPLY)
        mediaAttachmentService.attach(authorId, replyId, mediaIds)
        return PostDetail.of(reply, PostCounts(postId = replyId), mediaViewService.listForPost(replyId))
    }
}

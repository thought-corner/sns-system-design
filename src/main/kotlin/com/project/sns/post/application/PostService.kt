package com.project.sns.post.application

import com.project.sns.media.application.MediaAttachmentService
import com.project.sns.media.application.MediaDetail
import com.project.sns.media.application.MediaViewService
import com.project.sns.post.domain.NotPostAuthorException
import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostCountsRepository
import com.project.sns.post.domain.PostNotFoundException
import com.project.sns.post.domain.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostService(
    private val postRepository: PostRepository,
    private val postCountsRepository: PostCountsRepository,
    private val postTargetResolver: PostTargetResolver,
    private val mediaAttachmentService: MediaAttachmentService,
    private val mediaViewService: MediaViewService,
) {
    @Transactional
    fun create(authorId: Long, content: String, mediaIds: List<Long> = emptyList()): PostDetail {
        val post = postRepository.save(Post(authorId = authorId, content = content))
        val postId = requireNotNull(post.id)
        mediaAttachmentService.attach(authorId, postId, mediaIds)
        return PostDetail.of(post, PostCounts(postId = postId), mediaViewService.listForPost(postId))
    }

    @Transactional(readOnly = true)
    fun get(postId: Long): PostDetail {
        val post = postTargetResolver.getActive(postId)
        return PostDetail.of(post, postCountsRepository.get(postId), mediaViewService.listForPost(postId))
    }

    @Transactional
    fun delete(actorId: Long, postId: Long): PostChangeResult {
        val post = postRepository.findById(postId) ?: throw PostNotFoundException()
        if (post.authorId != actorId) {
            throw NotPostAuthorException()
        }
        val changed = postRepository.softDelete(postId)
        if (changed) {
            post.parentPostId?.let { postCountsRepository.decrease(it, PostCountDelta.REPLY) }
            post.quotedPostId?.let { postCountsRepository.decrease(it, PostCountDelta.QUOTE) }
            post.repostOfId?.let { postCountsRepository.decrease(it, PostCountDelta.REPOST) }
            if (post.repostOfId == null) {
                val cascaded = postRepository.softDeleteRepostsOf(postId)
                if (cascaded > 0) {
                    postCountsRepository.decrease(postId, PostCountDelta(repost = cascaded.toLong()))
                }
            }
        }
        return PostChangeResult(changed = changed)
    }
}

data class PostChangeResult(
    val changed: Boolean,
)

data class PostDetail(
    val id: Long,
    val authorId: Long,
    val content: String,
    val parentPostId: Long?,
    val quotedPostId: Long?,
    val repostOfId: Long?,
    val createdAt: java.time.Instant,
    val counts: PostCounts,
    val media: List<MediaDetail>,
) {
    companion object {
        fun of(post: Post, counts: PostCounts, media: List<MediaDetail> = emptyList()) = PostDetail(
            id = requireNotNull(post.id),
            authorId = post.authorId,
            content = post.content,
            parentPostId = post.parentPostId,
            quotedPostId = post.quotedPostId,
            repostOfId = post.repostOfId,
            createdAt = post.createdAt,
            counts = counts,
            media = media,
        )
    }
}

package com.project.sns.post.application

import com.project.sns.post.domain.NotPostAuthorException
import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostNotFoundException
import com.project.sns.post.domain.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostService(
    private val postRepository: PostRepository,
) {
    @Transactional
    fun create(authorId: Long, content: String): PostDetail {
        val post = postRepository.save(Post(authorId = authorId, content = content))
        return PostDetail.of(post, PostCounts(postId = requireNotNull(post.id)))
    }

    @Transactional(readOnly = true)
    fun get(postId: Long): PostDetail {
        val post = getActive(postId)
        return PostDetail.of(post, postRepository.getCounts(postId))
    }

    @Transactional
    fun delete(actorId: Long, postId: Long): PostChangeResult {
        val post = postRepository.findById(postId) ?: throw PostNotFoundException()
        if (post.authorId != actorId) {
            throw NotPostAuthorException()
        }
        val changed = postRepository.softDelete(postId)
        if (changed) {
            post.parentPostId?.let { postRepository.decreaseCounts(it, PostCountDelta.REPLY) }
            post.quotedPostId?.let { postRepository.decreaseCounts(it, PostCountDelta.QUOTE) }
            post.repostOfId?.let { postRepository.decreaseCounts(it, PostCountDelta.REPOST) }
            if (post.repostOfId == null) {
                val cascaded = postRepository.softDeleteRepostsOf(postId)
                if (cascaded > 0) {
                    postRepository.decreaseCounts(postId, PostCountDelta(repost = cascaded.toLong()))
                }
            }
        }
        return PostChangeResult(changed = changed)
    }

    @Transactional(readOnly = true)
    fun getActive(postId: Long): Post = postRepository.findActiveById(postId) ?: throw PostNotFoundException()

    /**
     * 쓰기 경로(답글·인용·리포스트·좋아요·조회 기록)의 대상 확인. 리포스트 행이면 원본으로 바꾼다.
     * 최종 대상만 `FOR SHARE` 로 다시 읽어 원본 삭제(캐스케이드)와 직렬화한다 — 잠금 없이 읽으면 삭제 트랜잭션이
     * `softDeleteRepostsOf` 를 지나간 뒤 커밋되는 리포스트 행이 캐스케이드를 비껴가 삭제된 원본을 가리킨 채 남는다.
     * 리포스트 행 자체는 잠그지 않는다: 삭제는 원본 → 리포스트 행 순으로 잠그므로 여기서 리포스트 행 → 원본 순으로 잡으면 교착한다.
     */
    @Transactional
    fun resolveTarget(postId: Long): Post {
        val post = getActive(postId)
        val targetId = post.repostOfId ?: requireNotNull(post.id)
        return postRepository.findActiveByIdForShare(targetId) ?: throw PostNotFoundException()
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
) {
    companion object {
        fun of(post: Post, counts: PostCounts) = PostDetail(
            id = requireNotNull(post.id),
            authorId = post.authorId,
            content = post.content,
            parentPostId = post.parentPostId,
            quotedPostId = post.quotedPostId,
            repostOfId = post.repostOfId,
            createdAt = post.createdAt,
            counts = counts,
        )
    }
}

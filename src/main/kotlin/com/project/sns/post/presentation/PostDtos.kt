package com.project.sns.post.presentation

import com.fasterxml.jackson.annotation.JsonProperty
import com.project.sns.post.application.PostDetail
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant

class PostContentRequest(@JsonProperty("content") content: String?) {
    @field:NotBlank(message = "본문은 비어 있을 수 없습니다.")
    @field:MaxCodePoints(500)
    @field:Pattern(regexp = "[^\u0000\uD800-\uDFFF]*", message = "본문에 허용되지 않는 문자가 있습니다.")
    val content: String = content?.trim() ?: ""
}

data class PostResponse(
    val id: Long,
    val authorId: Long,
    val content: String,
    val parentPostId: Long?,
    val quotedPostId: Long?,
    val repostOfId: Long?,
    val createdAt: Instant,
    val counts: PostCountsResponse,
) {
    companion object {
        fun from(detail: PostDetail) = PostResponse(
            id = detail.id,
            authorId = detail.authorId,
            content = detail.content,
            parentPostId = detail.parentPostId,
            quotedPostId = detail.quotedPostId,
            repostOfId = detail.repostOfId,
            createdAt = detail.createdAt,
            counts = PostCountsResponse(
                replyCount = detail.counts.replyCount,
                quoteCount = detail.counts.quoteCount,
                repostCount = detail.counts.repostCount,
                likeCount = detail.counts.likeCount,
                viewCount = detail.counts.viewCount,
            ),
        )
    }
}

data class PostCountsResponse(
    val replyCount: Long,
    val quoteCount: Long,
    val repostCount: Long,
    val likeCount: Long,
    val viewCount: Long,
)

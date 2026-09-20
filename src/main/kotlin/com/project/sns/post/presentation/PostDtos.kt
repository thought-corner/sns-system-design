package com.project.sns.post.presentation

import com.fasterxml.jackson.annotation.JsonProperty
import com.project.sns.media.application.MediaDetail
import com.project.sns.media.domain.MediaPolicy
import com.project.sns.post.application.PostDetail
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import org.hibernate.validator.constraints.UniqueElements

class PostContentRequest(
    @JsonProperty("content") content: String?,
    @JsonProperty("mediaIds") mediaIds: List<Long>? = null,
) {
    @field:NotBlank(message = "본문은 비어 있을 수 없습니다.")
    @field:MaxCodePoints(500)
    @field:Pattern(regexp = "[^\u0000\uD800-\uDFFF]*", message = "본문에 허용되지 않는 문자가 있습니다.")
    val content: String = content?.trim() ?: ""

    @field:NotNullElements(message = "mediaIds 에 null 이 있을 수 없습니다.")
    @field:Size(max = MediaPolicy.MAX_PER_POST, message = "미디어는 게시글당 ${MediaPolicy.MAX_PER_POST}개까지 첨부할 수 있습니다.")
    @field:UniqueElements(message = "같은 미디어를 두 번 첨부할 수 없습니다.")
    val mediaIds: List<Long> = mediaIds ?: emptyList()
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
    val media: List<MediaResponseItem>,
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
            media = detail.media.map(MediaResponseItem::from),
        )
    }
}

data class MediaResponseItem(
    val id: Long,
    val contentType: String,
    val width: Int?,
    val height: Int?,
    val url: String,
) {
    companion object {
        fun from(detail: MediaDetail) = MediaResponseItem(
            id = detail.id,
            contentType = detail.contentType,
            width = detail.width,
            height = detail.height,
            url = detail.url.toString(),
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

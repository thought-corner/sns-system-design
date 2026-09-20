package com.project.sns.post.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "post_counts")
class PostCounts(
    @Id
    @Column(name = "post_id")
    val postId: Long,
    @Column(name = "reply_count", nullable = false)
    val replyCount: Long = 0,
    @Column(name = "quote_count", nullable = false)
    val quoteCount: Long = 0,
    @Column(name = "repost_count", nullable = false)
    val repostCount: Long = 0,
    @Column(name = "like_count", nullable = false)
    val likeCount: Long = 0,
    @Column(name = "view_count", nullable = false)
    val viewCount: Long = 0,
)

data class PostCountDelta(
    val reply: Long = 0,
    val quote: Long = 0,
    val repost: Long = 0,
    val like: Long = 0,
    val view: Long = 0,
) {
    companion object {
        val REPLY = PostCountDelta(reply = 1)
        val QUOTE = PostCountDelta(quote = 1)
        val REPOST = PostCountDelta(repost = 1)
        val LIKE = PostCountDelta(like = 1)
        val VIEW = PostCountDelta(view = 1)
    }
}

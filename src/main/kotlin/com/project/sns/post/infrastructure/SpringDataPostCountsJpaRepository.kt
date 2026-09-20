package com.project.sns.post.infrastructure

import com.project.sns.post.domain.PostCounts
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringDataPostCountsJpaRepository : JpaRepository<PostCounts, Long> {
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO post_counts (post_id, reply_count, quote_count, repost_count, like_count, view_count)
            VALUES (:postId, :reply, :quote, :repost, :like, :view)
            ON CONFLICT (post_id) DO UPDATE
            SET reply_count  = post_counts.reply_count + EXCLUDED.reply_count,
                quote_count  = post_counts.quote_count + EXCLUDED.quote_count,
                repost_count = post_counts.repost_count + EXCLUDED.repost_count,
                like_count   = post_counts.like_count + EXCLUDED.like_count,
                view_count   = post_counts.view_count + EXCLUDED.view_count
        """,
    )
    fun addCounts(
        @Param("postId") postId: Long,
        @Param("reply") reply: Long,
        @Param("quote") quote: Long,
        @Param("repost") repost: Long,
        @Param("like") like: Long,
        @Param("view") view: Long,
    ): Int

    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE post_counts
            SET reply_count  = reply_count - :reply,
                quote_count  = quote_count - :quote,
                repost_count = repost_count - :repost,
                like_count   = like_count - :like,
                view_count   = view_count - :view
            WHERE post_id = :postId
              AND reply_count >= :reply
              AND quote_count >= :quote
              AND repost_count >= :repost
              AND like_count >= :like
              AND view_count >= :view
        """,
    )
    fun subtractCounts(
        @Param("postId") postId: Long,
        @Param("reply") reply: Long,
        @Param("quote") quote: Long,
        @Param("repost") repost: Long,
        @Param("like") like: Long,
        @Param("view") view: Long,
    ): Int
}

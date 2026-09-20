package com.project.sns.post.infrastructure

import com.project.sns.post.domain.PostLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringDataPostLikeJpaRepository : JpaRepository<PostLike, Long> {
    @Modifying
    @Query(
        value = """
            INSERT INTO post_likes (user_id, post_id, created_at)
            VALUES (:userId, :postId, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, post_id) WHERE deleted_at IS NULL DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("userId") userId: Long, @Param("postId") postId: Long): Int

    @Modifying
    @Query(
        value = """
            UPDATE post_likes
            SET deleted_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
              AND post_id = :postId
              AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDeleteRelation(@Param("userId") userId: Long, @Param("postId") postId: Long): Int

    @Query("SELECT COUNT(postLike) FROM PostLike postLike WHERE postLike.postId = :postId AND postLike.deletedAt IS NULL")
    fun countActiveByPostId(@Param("postId") postId: Long): Long
}

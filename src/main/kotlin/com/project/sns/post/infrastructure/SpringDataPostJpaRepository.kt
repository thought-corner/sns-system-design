package com.project.sns.post.infrastructure

import com.project.sns.post.domain.Post
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringDataPostJpaRepository : JpaRepository<Post, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Post?

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT post FROM Post post WHERE post.id = :id AND post.deletedAt IS NULL")
    fun findActiveByIdForShare(@Param("id") id: Long): Post?

    @Modifying
    @Query(
        value = """
            UPDATE posts
            SET deleted_at = CURRENT_TIMESTAMP
            WHERE id = :postId
              AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDeleteById(@Param("postId") postId: Long): Int

    @Query("SELECT COUNT(post) FROM Post post WHERE post.parentPostId = :postId AND post.deletedAt IS NULL")
    fun countActiveReplies(@Param("postId") postId: Long): Long

    @Query("SELECT COUNT(post) FROM Post post WHERE post.quotedPostId = :postId AND post.deletedAt IS NULL")
    fun countActiveQuotes(@Param("postId") postId: Long): Long

    @Modifying
    @Query(
        value = """
            INSERT INTO posts (author_id, content, repost_of_id, created_at)
            VALUES (:authorId, '', :originalId, CURRENT_TIMESTAMP)
            ON CONFLICT (author_id, repost_of_id) WHERE deleted_at IS NULL AND repost_of_id IS NOT NULL DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertRepostIfAbsent(@Param("authorId") authorId: Long, @Param("originalId") originalId: Long): Int

    @Modifying
    @Query(
        value = """
            UPDATE posts
            SET deleted_at = CURRENT_TIMESTAMP
            WHERE author_id = :authorId
              AND repost_of_id = :originalId
              AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDeleteRepost(@Param("authorId") authorId: Long, @Param("originalId") originalId: Long): Int

    @Modifying
    @Query(
        value = """
            UPDATE posts
            SET deleted_at = CURRENT_TIMESTAMP
            WHERE repost_of_id = :originalId
              AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDeleteRepostsOf(@Param("originalId") originalId: Long): Int

    @Query("SELECT COUNT(post) FROM Post post WHERE post.repostOfId = :postId AND post.deletedAt IS NULL")
    fun countActiveReposts(@Param("postId") postId: Long): Long
}

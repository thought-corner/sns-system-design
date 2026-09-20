package com.project.sns.media.infrastructure

import com.project.sns.media.domain.Media
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringDataMediaJpaRepository : JpaRepository<Media, Long> {
    fun findByIdAndOwnerIdAndDeletedAtIsNull(id: Long, ownerId: Long): Media?

    fun findByPostIdAndDeletedAtIsNullOrderByPositionAsc(postId: Long): List<Media>

    @Modifying
    @Query(
        value = """
            UPDATE media
            SET status = 'READY', ready_at = CURRENT_TIMESTAMP, width = :width, height = :height
            WHERE id = :id
              AND status = 'PENDING'
              AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun markReady(@Param("id") id: Long, @Param("width") width: Int?, @Param("height") height: Int?): Int

    @Modifying
    @Query(
        value = """
            UPDATE media
            SET post_id = :postId, position = :position
            WHERE id = :id
              AND owner_id = :ownerId
              AND status = 'READY'
              AND post_id IS NULL
              AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun attach(
        @Param("id") id: Long,
        @Param("ownerId") ownerId: Long,
        @Param("postId") postId: Long,
        @Param("position") position: Int,
    ): Int

    @Modifying
    @Query(
        value = """
            UPDATE media
            SET deleted_at = CURRENT_TIMESTAMP
            WHERE id = :id
              AND status = 'PENDING'
              AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDeletePendingById(@Param("id") id: Long): Int
}

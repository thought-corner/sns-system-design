package com.project.sns.follow.infrastructure

import com.project.sns.follow.domain.Follow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringDataFollowJpaRepository : JpaRepository<Follow, Long> {
    @Modifying
    @Query(
        value = """
            INSERT INTO follows (follower_id, following_id, created_at)
            VALUES (:followerId, :followingId, CURRENT_TIMESTAMP)
            ON CONFLICT (follower_id, following_id) WHERE deleted_at IS NULL DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("followerId") followerId: Long,
        @Param("followingId") followingId: Long,
    ): Int

    @Modifying
    @Query(
        value = """
            UPDATE follows
            SET deleted_at = CURRENT_TIMESTAMP
            WHERE follower_id = :followerId
              AND following_id = :followingId
              AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDeleteRelation(
        @Param("followerId") followerId: Long,
        @Param("followingId") followingId: Long,
    ): Int

    @Query(
        """
            SELECT COUNT(follow)
            FROM Follow follow
            WHERE follow.followingId = :userId
              AND follow.deletedAt IS NULL
        """,
    )
    fun countFollowers(@Param("userId") userId: Long): Long

    @Query(
        """
            SELECT COUNT(follow)
            FROM Follow follow
            WHERE follow.followerId = :userId
              AND follow.deletedAt IS NULL
        """,
    )
    fun countFollowing(@Param("userId") userId: Long): Long
}

package com.project.sns.timeline.infrastructure

import com.project.sns.post.domain.Post
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface SpringDataTimelineQueryRepository : Repository<Post, Long> {
    @Query(
        value = """
            SELECT follower_id
            FROM follows
            WHERE following_id = :followingId
              AND deleted_at IS NULL
              AND follower_id > :afterFollowerId
            ORDER BY follower_id
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findFollowerIds(
        @Param("followingId") followingId: Long,
        @Param("afterFollowerId") afterFollowerId: Long,
        @Param("limit") limit: Int,
    ): List<Long>

    @Query(
        value = """
            SELECT id
            FROM posts
            WHERE author_id = :authorId
              AND deleted_at IS NULL
              AND parent_post_id IS NULL
            ORDER BY id DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findRecentPostIdsByAuthor(@Param("authorId") authorId: Long, @Param("limit") limit: Int): List<Long>

    @Query(
        value = """
            SELECT following_id
            FROM follows
            WHERE follower_id = :userId
              AND deleted_at IS NULL
            ORDER BY following_id
        """,
        nativeQuery = true,
    )
    fun findFollowingIds(@Param("userId") userId: Long): List<Long>

    @Query(
        value = """
            SELECT id
            FROM posts
            WHERE author_id IN (:authorIds)
              AND deleted_at IS NULL
              AND parent_post_id IS NULL
            ORDER BY id DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findRecentPostIdsByAuthors(
        @Param("authorIds") authorIds: Collection<Long>,
        @Param("limit") limit: Int
    ): List<Long>

    @Query(
        value = """
            SELECT f.following_id
            FROM follows f
            JOIN follow_counts c ON c.user_id = f.following_id
            WHERE f.follower_id = :userId
              AND f.deleted_at IS NULL
              AND c.follower_count >= :threshold
            ORDER BY f.following_id
        """,
        nativeQuery = true,
    )
    fun findFollowedCelebrityIds(@Param("userId") userId: Long, @Param("threshold") threshold: Long): List<Long>

    @Query(
        value = "SELECT COALESCE((SELECT follower_count FROM follow_counts WHERE user_id = :userId), 0)",
        nativeQuery = true
    )
    fun followerCount(@Param("userId") userId: Long): Long

    @Query(
        value = """
            SELECT p.id
            FROM posts p
            JOIN post_counts c ON c.post_id = p.id
            WHERE p.deleted_at IS NULL
              AND p.parent_post_id IS NULL
              AND p.repost_of_id IS NULL
              AND p.created_at >= :since
              AND c.like_count >= :minLikes
              AND (:beforeId IS NULL OR p.id < :beforeId)
            ORDER BY p.id DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findPopularPostIds(
        @Param("since") since: java.time.Instant,
        @Param("minLikes") minLikes: Long,
        @Param("beforeId") beforeId: Long?,
        @Param("limit") limit: Int,
    ): List<Long>
}

package com.project.sns.follow.infrastructure

import com.project.sns.follow.domain.FollowCounts
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringDataFollowCountsJpaRepository : JpaRepository<FollowCounts, Long> {
    /** 행이 없으면 만들고 있으면 더한다. 한 트랜잭션에서 두 사용자를 갱신할 땐 user_id 오름차순으로 호출해 교착을 막는다. */
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO follow_counts (user_id, follower_count, following_count)
            VALUES (:userId, :followerDelta, :followingDelta)
            ON CONFLICT (user_id) DO UPDATE
            SET follower_count  = follow_counts.follower_count + EXCLUDED.follower_count,
                following_count = follow_counts.following_count + EXCLUDED.following_count
        """,
    )
    fun addCounts(
        @Param("userId") userId: Long,
        @Param("followerDelta") followerDelta: Long,
        @Param("followingDelta") followingDelta: Long,
    ): Int

    /** 0 아래로 내리지 않는다 — 영향 행 0 은 카운터 행이 없거나 이미 0 이라는 뜻(드리프트 신호). */
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            UPDATE follow_counts
            SET follower_count  = follower_count - :followerDelta,
                following_count = following_count - :followingDelta
            WHERE user_id = :userId
              AND follower_count >= :followerDelta
              AND following_count >= :followingDelta
        """,
    )
    fun subtractCounts(
        @Param("userId") userId: Long,
        @Param("followerDelta") followerDelta: Long,
        @Param("followingDelta") followingDelta: Long,
    ): Int
}

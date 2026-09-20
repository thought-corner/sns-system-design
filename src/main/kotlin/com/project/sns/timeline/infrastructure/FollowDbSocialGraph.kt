package com.project.sns.timeline.infrastructure

import com.project.sns.timeline.domain.SocialGraph
import org.springframework.stereotype.Repository

@Repository
class FollowDbSocialGraph(
    private val jpaRepository: SpringDataTimelineQueryRepository,
) : SocialGraph {
    override fun followingIds(userId: Long): List<Long> = jpaRepository.findFollowingIds(userId)

    override fun followerIds(followingId: Long, afterFollowerId: Long, limit: Int): List<Long> =
        jpaRepository.findFollowerIds(followingId, afterFollowerId, limit)

    override fun followerCount(userId: Long): Long = jpaRepository.followerCount(userId)

    override fun followedCelebrityIds(userId: Long, celebrityThreshold: Long): List<Long> =
        jpaRepository.findFollowedCelebrityIds(userId, celebrityThreshold)
}

package com.project.sns.timeline.application

import com.project.sns.timeline.domain.FollowCache
import com.project.sns.timeline.domain.SocialGraph
import com.project.sns.timeline.domain.TimelinePolicy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FollowGraphService(
    private val followCache: FollowCache,
    private val socialGraph: SocialGraph,
) {
    fun followingIds(userId: Long): Set<Long> {
        safely("팔로잉 조회") { followCache.followingIds(userId) }?.let { return it }
        val fromDb = socialGraph.followingIds(userId)
        safely("팔로잉 저장") { followCache.putFollowingIds(userId, fromDb) }
        return fromDb.toSet()
    }

    fun followedCelebrityIds(userId: Long): Set<Long> {
        safely("대형 계정 조회") { followCache.celebrityIds(userId) }?.let { return it }
        val fromDb = socialGraph.followedCelebrityIds(userId, TimelinePolicy.CELEBRITY_MAX_FOLLOWERS)
        safely("대형 계정 저장") { followCache.putCelebrityIds(userId, fromDb) }
        return fromDb.toSet()
    }

    fun isCelebrity(userId: Long): Boolean = socialGraph.followerCount(userId) >= TimelinePolicy.CELEBRITY_MAX_FOLLOWERS

    fun onFollowChanged(followerId: Long) {
        followCache.evictFollowingIds(followerId)
        followCache.evictCelebrityIds(followerId)
    }

    private fun <T> safely(what: String, action: () -> T): T? = try {
        action()
    } catch (e: RuntimeException) {
        logger.warn("팔로우 캐시 {} 실패 — PostgreSQL 로 진행", what, e)
        null
    }

    private companion object {
        val logger = LoggerFactory.getLogger(FollowGraphService::class.java)
    }
}

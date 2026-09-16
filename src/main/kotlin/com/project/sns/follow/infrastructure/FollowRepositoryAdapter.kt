package com.project.sns.follow.infrastructure

import com.project.sns.follow.domain.FollowCounts
import com.project.sns.follow.domain.FollowRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class FollowRepositoryAdapter(
    private val jpaRepository: SpringDataFollowJpaRepository,
    private val countsRepository: SpringDataFollowCountsJpaRepository,
) : FollowRepository {
    override fun create(followerId: Long, followingId: Long): Boolean =
        jpaRepository.insertIfAbsent(followerId, followingId) == 1

    override fun softDelete(followerId: Long, followingId: Long): Boolean =
        jpaRepository.softDeleteRelation(followerId, followingId) == 1

    override fun increaseCounts(followerId: Long, followingId: Long) {
        // 두 카운터 행을 항상 user_id 오름차순으로 잠근다 — A→B 와 B→A 가 동시에 오면 반대 순서로 잠가 교착이 난다.
        forEachInLockOrder(followerId, followingId) { userId, followerDelta, followingDelta ->
            countsRepository.addCounts(userId, followerDelta, followingDelta)
        }
    }

    override fun decreaseCounts(followerId: Long, followingId: Long) {
        forEachInLockOrder(followerId, followingId) { userId, followerDelta, followingDelta ->
            if (countsRepository.subtractCounts(userId, followerDelta, followingDelta) == 0) {
                // 관계는 있었는데 카운터가 없거나 0 — 정합성이 깨진 상태. 요청은 성공시키고 재계산 절차로 복구한다(설계 문서 "2. 팔로워 수와 팔로잉 수 — 개정" 의 재계산 SQL).
                logger.warn("follow_counts 감소 실패: userId={} followerDelta={} followingDelta={}", userId, followerDelta, followingDelta)
            }
        }
    }

    override fun getCounts(userId: Long): FollowCounts =
        countsRepository.findById(userId).orElseGet { FollowCounts(userId = userId) }

    private inline fun forEachInLockOrder(
        followerId: Long,
        followingId: Long,
        apply: (userId: Long, followerDelta: Long, followingDelta: Long) -> Unit,
    ) {
        // 대상(followingId)은 팔로워 수, 행위자(followerId)는 팔로잉 수가 바뀐다.
        val deltas = listOf(followingId to (1L to 0L), followerId to (0L to 1L)).sortedBy { it.first }
        deltas.forEach { (userId, delta) -> apply(userId, delta.first, delta.second) }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(FollowRepositoryAdapter::class.java)
    }
}

package com.project.sns.follow.application

import com.project.sns.follow.domain.FollowRepository
import com.project.sns.follow.domain.SelfFollowNotAllowedException
import com.project.sns.user.application.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FollowService(
    private val userService: UserService,
    private val followRepository: FollowRepository,
) {
    @Transactional
    fun follow(followerId: Long, followingId: Long): FollowResult {
        validateDifferentUsers(followerId, followingId)
        userService.getById(followingId)
        // 관계가 실제로 생겼을 때만 같은 트랜잭션에서 카운터를 올린다 — 멱등 재시도·동시 요청은 create 가 false 라 두 번 세지 않는다.
        val changed = followRepository.create(followerId, followingId)
        if (changed) {
            followRepository.increaseCounts(followerId, followingId)
        }
        return FollowResult(changed = changed)
    }

    @Transactional
    fun unfollow(followerId: Long, followingId: Long): FollowResult {
        validateDifferentUsers(followerId, followingId)
        userService.getById(followingId)
        val changed = followRepository.softDelete(followerId, followingId)
        if (changed) {
            followRepository.decreaseCounts(followerId, followingId)
        }
        return FollowResult(changed = changed)
    }

    @Transactional(readOnly = true)
    fun getStats(userId: Long): FollowStats {
        userService.getById(userId)
        val counts = followRepository.getCounts(userId)
        return FollowStats(
            followerCount = counts.followerCount,
            followingCount = counts.followingCount,
        )
    }

    private fun validateDifferentUsers(followerId: Long, followingId: Long) {
        if (followerId == followingId) {
            throw SelfFollowNotAllowedException()
        }
    }
}

data class FollowResult(
    val changed: Boolean,
)

data class FollowStats(
    val followerCount: Long,
    val followingCount: Long,
)

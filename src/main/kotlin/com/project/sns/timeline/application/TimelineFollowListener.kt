package com.project.sns.timeline.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class TimelineFollowListener(
    private val fanoutService: TimelineFanoutService,
    private val followGraphService: FollowGraphService,
) {
    fun afterFollow(followerId: Long, followingId: Long) = afterCommit("팔로우 반영") {
        followGraphService.onFollowChanged(followerId)
        fanoutService.backfillAfterFollow(followerId, followingId)
    }

    fun afterUnfollow(followerId: Long, followingId: Long) = afterCommit("언팔로우 반영") {
        followGraphService.onFollowChanged(followerId)
        fanoutService.removeAfterUnfollow(followerId, followingId)
    }

    private fun afterCommit(what: String, action: () -> Unit) {
        val guarded = {
            try {
                action()
            } catch (e: RuntimeException) {
                logger.warn("타임라인 {} 실패 — 재구축 때 복구된다", what, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            guarded()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = guarded()
            },
        )
    }

    private companion object {
        val logger = LoggerFactory.getLogger(TimelineFollowListener::class.java)
    }
}

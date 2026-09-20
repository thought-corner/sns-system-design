package com.project.sns.timeline.application

import com.project.sns.timeline.domain.FanoutEvent
import com.project.sns.timeline.domain.FanoutQueue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class TimelineFanoutPublisher(
    private val fanoutQueue: FanoutQueue,
) {
    fun publishAfterCommit(postId: Long, authorId: Long) {
        val event = FanoutEvent(postId = postId, authorId = authorId)
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            enqueue(event)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = enqueue(event)
            },
        )
    }

    private fun enqueue(event: FanoutEvent) {
        try {
            fanoutQueue.enqueue(event)
        } catch (e: RuntimeException) {
            logger.warn("타임라인 팬아웃 발행 실패 — 재구축 때 복구된다: postId={} authorId={}", event.postId, event.authorId, e)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(TimelineFanoutPublisher::class.java)
    }
}

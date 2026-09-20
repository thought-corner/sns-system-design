package com.project.sns.timeline.domain

import java.time.Duration

data class FanoutEvent(
    val postId: Long,
    val authorId: Long,
)

data class FanoutMessage(
    val id: String,
    val event: FanoutEvent,
    val deliveryCount: Long,
)

interface FanoutQueue {
    fun enqueue(event: FanoutEvent)

    fun poll(consumer: String, count: Int, block: Duration): List<FanoutMessage>

    fun claimStale(consumer: String, minIdle: Duration, count: Int): List<FanoutMessage>

    fun ack(messageId: String)
}

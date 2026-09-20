package com.project.sns.timeline.infrastructure

import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.timeline.domain.FanoutEvent
import com.project.sns.timeline.domain.FanoutMessage
import com.project.sns.timeline.domain.FanoutQueue
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Range
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate

class RedisFanoutQueue(
    private val redisTemplate: StringRedisTemplate,
) : FanoutQueue {
    private val stream = TimelineKeys.FANOUT_STREAM
    private val group = TimelineKeys.FANOUT_GROUP

    fun ensureGroup() {
        try {
            redisTemplate.opsForStream<String, String>().createGroup(stream, ReadOffset.from("0-0"), group)
        } catch (e: RedisSystemException) {
            if (e.cause?.message?.contains("BUSYGROUP") != true) throw e
        }
    }

    override fun enqueue(event: FanoutEvent) {
        val record = MapRecord.create(
            stream,
            mapOf("postId" to event.postId.toString(), "authorId" to event.authorId.toString())
        )
        redisTemplate.opsForStream<String, String>().add(record)
        redisTemplate.opsForStream<String, String>().trim(stream, TimelinePolicy.Fanout.STREAM_MAX_LENGTH, true)
    }

    override fun poll(consumer: String, count: Int, block: Duration): List<FanoutMessage> {
        val records = redisTemplate.opsForStream<String, String>().read(
            Consumer.from(group, consumer),
            StreamReadOptions.empty().count(count.toLong()).block(block),
            StreamOffset.create(stream, ReadOffset.lastConsumed()),
        ) ?: emptyList()
        return records.mapNotNull { toMessage(it, deliveryCount = 1) }
    }

    override fun claimStale(consumer: String, minIdle: Duration, count: Int): List<FanoutMessage> {
        val ops = redisTemplate.opsForStream<String, String>()
        val pending = ops.pending(stream, group, Range.unbounded<String>(), count.toLong()) ?: return emptyList()
        val stale = pending.toList().filter { it.elapsedTimeSinceLastDelivery >= minIdle }
        if (stale.isEmpty()) return emptyList()
        val deliveries = stale.associate { it.idAsString to it.totalDeliveryCount }
        val ids: Array<RecordId> = stale.map { it.id }.toTypedArray()
        val claimed = ops.claim(stream, group, consumer, minIdle, *ids) ?: emptyList()
        return claimed.mapNotNull { toMessage(it, deliveryCount = (deliveries[it.id.value] ?: 0L) + 1) }
    }

    override fun ack(messageId: String) {
        redisTemplate.opsForStream<String, String>().acknowledge(stream, group, RecordId.of(messageId))
    }

    private fun toMessage(record: MapRecord<String, String, String>, deliveryCount: Long): FanoutMessage? {
        val postId = record.value["postId"]?.toLongOrNull()
        val authorId = record.value["authorId"]?.toLongOrNull()
        if (postId == null || authorId == null) {
            logger.warn("팬아웃 메시지 형식 오류 — ack 로 버림: id={} value={}", record.id.value, record.value)
            ack(record.id.value)
            return null
        }
        return FanoutMessage(
            id = record.id.value,
            event = FanoutEvent(postId = postId, authorId = authorId),
            deliveryCount = deliveryCount
        )
    }

    private companion object {
        val logger = LoggerFactory.getLogger(RedisFanoutQueue::class.java)
    }
}

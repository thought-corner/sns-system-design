package com.project.sns.timeline.infrastructure

import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.timeline.domain.PostCache
import com.project.sns.timeline.domain.PostSnapshot
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate
import tools.jackson.databind.ObjectMapper

class RedisPostCache(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : PostCache {
    override fun getAll(ids: Collection<Long>): Map<Long, PostSnapshot> {
        if (ids.isEmpty()) return emptyMap()
        val ordered = ids.toList()
        val values = redisTemplate.opsForValue().multiGet(ordered.map { TimelineKeys.post(it) }) ?: return emptyMap()
        return ordered.zip(values)
            .mapNotNull { (id, json) -> json?.let { id to objectMapper.readValue(it, PostSnapshot::class.java) } }
            .toMap()
    }

    override fun putAll(snapshots: Collection<PostSnapshot>) {
        if (snapshots.isEmpty()) return
        val ttlSeconds = TimelinePolicy.POST_CACHE_TTL.seconds
        redisTemplate.executePipelined(
            RedisCallback<Any?> { connection ->
                snapshots.forEach { snapshot ->
                    connection.stringCommands().setEx(
                        TimelineKeys.post(snapshot.id).toByteArray(),
                        ttlSeconds,
                        objectMapper.writeValueAsBytes(snapshot),
                    )
                }
                null
            },
        )
    }

    override fun evict(id: Long) {
        redisTemplate.delete(TimelineKeys.post(id))
    }
}

package com.project.sns.timeline.infrastructure

import com.project.sns.timeline.domain.FollowCache
import com.project.sns.timeline.domain.TimelinePolicy
import org.springframework.data.redis.connection.ReturnType
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate

class RedisFollowCache(
    private val redisTemplate: StringRedisTemplate,
) : FollowCache {
    override fun followingIds(userId: Long): Set<Long>? = members(TimelineKeys.following(userId))

    override fun putFollowingIds(userId: Long, ids: Collection<Long>) = put(TimelineKeys.following(userId), ids)

    override fun evictFollowingIds(userId: Long) {
        redisTemplate.delete(TimelineKeys.following(userId))
    }

    override fun celebrityIds(userId: Long): Set<Long>? = members(TimelineKeys.celebrities(userId))

    override fun putCelebrityIds(userId: Long, ids: Collection<Long>) = put(TimelineKeys.celebrities(userId), ids)

    override fun evictCelebrityIds(userId: Long) {
        redisTemplate.delete(TimelineKeys.celebrities(userId))
    }

    private fun members(key: String): Set<Long>? {
        val raw = redisTemplate.opsForSet().members(key)
        if (raw.isNullOrEmpty()) return null
        return raw.filter { it != EMPTY_MARKER }.map { it.toLong() }.toSet()
    }

    private fun put(key: String, ids: Collection<Long>) {
        val values = if (ids.isEmpty()) listOf(EMPTY_MARKER) else ids.map { it.toString() }
        val args = (listOf(TimelinePolicy.FOLLOW_CACHE_TTL.seconds.toString()) + values).map { it.toByteArray() }.toTypedArray()
        redisTemplate.execute(
            RedisCallback<Long?> { connection ->
                connection.scriptingCommands().eval(REPLACE_WITH_TTL, ReturnType.INTEGER, 1, key.toByteArray(), *args)
            },
        )
    }

    private companion object {
        const val EMPTY_MARKER = "-"
        val REPLACE_WITH_TTL: ByteArray = """
            redis.call('DEL', KEYS[1])
            for i = 2, #ARGV, 1000 do
                redis.call('SADD', KEYS[1], unpack(ARGV, i, math.min(i + 999, #ARGV)))
            end
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            return 1
        """.trimIndent().toByteArray()
    }
}

package com.project.sns.timeline.infrastructure

import com.project.sns.timeline.domain.TimelineEntry
import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.timeline.domain.TimelineSlice
import com.project.sns.timeline.domain.TimelineStore
import org.springframework.data.redis.connection.ReturnType
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.util.concurrent.TimeUnit

class RedisTimelineStore(
    private val redisTemplate: StringRedisTemplate,
) : TimelineStore {
    override fun pushHome(userIds: Collection<Long>, entry: TimelineEntry) {
        if (userIds.isEmpty()) return
        val args = pushArgs(entry, TimelinePolicy.HOME_TIMELINE_SIZE)
        redisTemplate.executePipelined(
            RedisCallback<Any?> { connection ->
                userIds.forEach { userId ->
                    connection.scriptingCommands().eval<Long>(PUSH_IF_EXISTS, ReturnType.INTEGER, 1, TimelineKeys.home(userId).toByteArray(), *args)
                }
                null
            },
        )
    }

    override fun pushAuthor(authorId: Long, entry: TimelineEntry) {
        val args = pushArgs(entry, TimelinePolicy.AUTHOR_TIMELINE_SIZE)
        redisTemplate.execute(
            RedisCallback<Long?> { connection ->
                connection.scriptingCommands().eval(PUSH_IF_EXISTS, ReturnType.INTEGER, 1, TimelineKeys.author(authorId).toByteArray(), *args)
            },
        )
    }

    override fun removeHome(userId: Long, postIds: Collection<Long>) {
        if (postIds.isEmpty()) return
        redisTemplate.opsForZSet().remove(TimelineKeys.home(userId), *postIds.map { it.toString() }.toTypedArray())
    }

    override fun readHome(userId: Long, beforeScore: Long?, limit: Int): TimelineSlice = read(TimelineKeys.home(userId), beforeScore, limit)

    override fun readAuthor(authorId: Long, beforeScore: Long?, limit: Int): TimelineSlice = read(TimelineKeys.author(authorId), beforeScore, limit)

    override fun beginRebuildHome(userId: Long) = beginRebuild(TimelineKeys.home(userId))

    override fun rebuildHome(userId: Long, entries: List<TimelineEntry>) = rebuild(TimelineKeys.home(userId), entries, TimelinePolicy.HOME_TIMELINE_SIZE)

    override fun beginRebuildAuthor(authorId: Long) = beginRebuild(TimelineKeys.author(authorId))

    override fun rebuildAuthor(authorId: Long, entries: List<TimelineEntry>) = rebuild(TimelineKeys.author(authorId), entries, TimelinePolicy.AUTHOR_TIMELINE_SIZE)

    private fun pushArgs(entry: TimelineEntry, size: Int): Array<ByteArray> = arrayOf(
        entry.score.toString().toByteArray(),
        entry.postId.toString().toByteArray(),
        (-(size.toLong() + 1)).toString().toByteArray(),
        TimelinePolicy.IDLE_TTL.seconds.toString().toByteArray(),
    )

    private fun read(key: String, beforeScore: Long?, limit: Int): TimelineSlice {
        val zset = redisTemplate.opsForZSet()
        if (zset.score(key, SENTINEL) != null) return TimelineSlice(exists = false, entries = emptyList())
        val max = beforeScore?.let { it.toDouble() - 1 } ?: Double.POSITIVE_INFINITY
        val tuples = zset.reverseRangeByScoreWithScores(key, Double.NEGATIVE_INFINITY, max, 0, limit.toLong()) ?: emptySet()
        if (tuples.any { it.value == SENTINEL }) return TimelineSlice(exists = false, entries = emptyList())
        if (tuples.isEmpty() && (zset.size(key) ?: 0L) == 0L) return TimelineSlice(exists = false, entries = emptyList())
        redisTemplate.expire(key, TimelinePolicy.IDLE_TTL.seconds, TimeUnit.SECONDS)
        return TimelineSlice(
            exists = true,
            entries = tuples.map { TimelineEntry(postId = requireNotNull(it.value).toLong(), score = requireNotNull(it.score).toLong()) },
        )
    }

    private fun beginRebuild(key: String) {
        redisTemplate.opsForZSet().addIfAbsent(key, SENTINEL, SENTINEL_SCORE)
        redisTemplate.expire(key, TimelinePolicy.REBUILD_SENTINEL_TTL.seconds, TimeUnit.SECONDS)
    }

    private fun rebuild(key: String, entries: List<TimelineEntry>, size: Int) {
        val tuples = entries.sortedByDescending { it.score }.take(size)
            .map { ZSetOperations.TypedTuple.of(it.postId.toString(), it.score.toDouble()) }.toSet()
        if (tuples.isNotEmpty()) redisTemplate.opsForZSet().add(key, tuples)
        redisTemplate.opsForZSet().remove(key, SENTINEL)
        if (redisTemplate.hasKey(key) != true) return
        redisTemplate.opsForZSet().removeRange(key, 0, -(size.toLong() + 1))
        redisTemplate.expire(key, TimelinePolicy.IDLE_TTL.seconds, TimeUnit.SECONDS)
    }

    private companion object {
        const val SENTINEL = "-"
        const val SENTINEL_SCORE = -1.0
        val PUSH_IF_EXISTS: ByteArray = """
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
            redis.call('ZREMRANGEBYRANK', KEYS[1], 0, ARGV[3])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return 1
        """.trimIndent().toByteArray()
    }
}

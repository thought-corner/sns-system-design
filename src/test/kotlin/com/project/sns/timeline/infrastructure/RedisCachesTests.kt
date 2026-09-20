package com.project.sns.timeline.infrastructure

import com.project.sns.timeline.domain.MediaSnapshot
import com.project.sns.timeline.domain.PostSnapshot
import com.project.sns.timeline.domain.TimelinePolicy
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class RedisCachesTests {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var template: StringRedisTemplate
    private lateinit var followCache: RedisFollowCache
    private lateinit var postCache: RedisPostCache

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(
            redis.host,
            redis.getMappedPort(REDIS_PORT)
        ).apply { afterPropertiesSet(); start() }
        template = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        template.execute { it.serverCommands().flushAll() }
        followCache = RedisFollowCache(template)
        postCache = RedisPostCache(template, JsonMapper.builder().findAndAddModules().build())
    }

    @AfterEach
    fun tearDown() = connectionFactory.destroy()

    @Test
    fun `팔로우 캐시는 미스와 빈 집합을 구분하고 evict 로 비워진다`() {
        assertNull(followCache.followingIds(1L))

        followCache.putFollowingIds(1L, emptyList())
        assertEquals(emptySet(), followCache.followingIds(1L))
        assertTrue(template.getExpire(TimelineKeys.following(1L)) in 1..TimelinePolicy.FOLLOW_CACHE_TTL.seconds, "빈 집합 마커에도 TTL 이 붙는다")

        followCache.putFollowingIds(1L, listOf(2L, 3L))
        assertEquals(setOf(2L, 3L), followCache.followingIds(1L))
        assertTrue(template.getExpire(TimelineKeys.following(1L)) in 1..TimelinePolicy.FOLLOW_CACHE_TTL.seconds)

        followCache.evictFollowingIds(1L)
        assertNull(followCache.followingIds(1L))

        followCache.putCelebrityIds(1L, listOf(7L))
        assertEquals(setOf(7L), followCache.celebrityIds(1L))
        assertTrue(template.getExpire(TimelineKeys.celebrities(1L)) in 1..TimelinePolicy.FOLLOW_CACHE_TTL.seconds)
        followCache.putCelebrityIds(1L, listOf(8L))
        assertEquals(setOf(8L), followCache.celebrityIds(1L), "put 은 이전 집합을 통째로 바꾼다")
        followCache.evictCelebrityIds(1L)
        assertNull(followCache.celebrityIds(1L))
    }

    @Test
    fun `팔로잉이 Lua unpack 상한을 넘어도 통째로 들어간다`() {
        val ids = (1L..10_000L).toList()

        followCache.putFollowingIds(2L, ids)

        assertEquals(ids.toSet(), followCache.followingIds(2L))
        assertTrue(template.getExpire(TimelineKeys.following(2L)) in 1..TimelinePolicy.FOLLOW_CACHE_TTL.seconds)
    }

    @Test
    fun `게시글 캐시는 스냅샷을 JSON 으로 왕복하고 evict 로 빠진다`() {
        val snapshot = PostSnapshot(
            id = 10L, authorId = 1L, content = "본문", parentPostId = null, quotedPostId = 5L, repostOfId = null,
            createdAt = Instant.parse("2026-09-20T00:00:00Z"),
            media = listOf(
                MediaSnapshot(
                    id = 3L,
                    contentType = "image/png",
                    sizeBytes = 100,
                    width = 2,
                    height = 3,
                    storageKey = "media/1/3.png"
                )
            ),
        )
        postCache.putAll(listOf(snapshot))

        val loaded = postCache.getAll(listOf(10L, 11L))
        assertEquals(setOf(10L), loaded.keys)
        assertEquals("본문", loaded.getValue(10L).content)
        assertEquals(5L, loaded.getValue(10L).quotedPostId)
        assertEquals("media/1/3.png", loaded.getValue(10L).media.single().storageKey)
        assertEquals(Instant.parse("2026-09-20T00:00:00Z"), loaded.getValue(10L).createdAt)
        assertTrue(template.getExpire(TimelineKeys.post(10L)) in 1..TimelinePolicy.POST_CACHE_TTL.seconds)

        postCache.evict(10L)
        assertTrue(postCache.getAll(listOf(10L)).isEmpty())
    }

    private class RedisContainer : GenericContainer<RedisContainer>("redis:7.4-alpine")

    private companion object {
        const val REDIS_PORT = 6379

        @Container
        @JvmField
        val redis = RedisContainer().withExposedPorts(REDIS_PORT)
    }
}

package com.project.sns.timeline.infrastructure

import com.project.sns.timeline.domain.FanoutEvent
import com.project.sns.timeline.domain.TimelineEntry
import com.project.sns.timeline.domain.TimelinePolicy
import java.time.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class RedisTimelineStoreTests {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var template: StringRedisTemplate
    private lateinit var store: RedisTimelineStore
    private lateinit var queue: RedisFanoutQueue

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(
            redis.host,
            redis.getMappedPort(REDIS_PORT)
        ).apply { afterPropertiesSet(); start() }
        template = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        template.execute { it.serverCommands().flushAll() }
        store = RedisTimelineStore(template)
        queue = RedisFanoutQueue(template).also { it.ensureGroup() }
    }

    @AfterEach
    fun tearDown() = connectionFactory.destroy()

    @Test
    fun `홈 타임라인은 id 내림차순으로 읽히고 상한을 넘으면 오래된 것이 밀려난다`() {
        store.rebuildHome(1L, listOf(e(10L)))
        store.rebuildHome(2L, listOf(e(10L)))
        store.pushHome(listOf(1L), e(12L))
        store.pushHome(listOf(1L), e(11L))
        store.pushHome(listOf(1L), e(13L))

        assertEquals(listOf(13L, 12L, 11L, 10L), store.readHome(1L, null, 10).entries.map { it.postId })
        assertEquals(listOf(11L, 10L), store.readHome(1L, 120L, 10).entries.map { it.postId })
        assertEquals(listOf(e(10L)), store.readHome(2L, null, 10).entries)
        assertTrue(template.getExpire(TimelineKeys.home(1L)) in 1..TimelinePolicy.IDLE_TTL.seconds)

        store.rebuildHome(3L, listOf(e(100L)))
        (101L..100L + TimelinePolicy.HOME_TIMELINE_SIZE).forEach { store.pushHome(listOf(3L), e(it)) }
        val capped = store.readHome(3L, null, TimelinePolicy.HOME_TIMELINE_SIZE + 10).entries.map { it.postId }
        assertEquals(TimelinePolicy.HOME_TIMELINE_SIZE, capped.size)
        assertEquals(101L, capped.last())
    }

    @Test
    fun `없는 키는 exists 가 거짓이고 재구축하면 채워지며 제거는 특정 항목만 뺀다`() {
        assertFalse(store.readHome(9L, null, 10).exists)

        store.rebuildHome(9L, listOf(e(30L), e(20L), e(10L), e(5L)))
        assertEquals(listOf(30L, 20L, 10L, 5L), store.readHome(9L, null, 10).entries.map { it.postId })

        store.removeHome(9L, listOf(20L))
        assertEquals(listOf(30L, 10L, 5L), store.readHome(9L, null, 10).entries.map { it.postId })

        store.removeHome(9L, listOf(30L, 10L, 5L))
        assertFalse(store.readHome(9L, null, 10).exists, "항목이 0개가 되면 Redis 가 키를 지워 다음 읽기는 재구축이다")
    }

    @Test
    fun `키가 없는 타임라인엔 push 가 들어가지 않는다 — 재구축이 만든다`() {
        store.pushHome(listOf(8L), e(1L))
        store.pushAuthor(8L, e(1L))
        assertFalse(store.readHome(8L, null, 10).exists)
        assertFalse(store.readAuthor(8L, null, 10).exists)
    }

    @Test
    fun `재구축 창에 도착한 push 는 살아남고 센티널은 읽히지 않는다`() {
        store.beginRebuildHome(5L)
        assertFalse(store.readHome(5L, null, 10).exists, "센티널이 남아 있으면 미구축 — 다음 읽기가 재구축한다")
        assertTrue(template.getExpire(TimelineKeys.home(5L)) in 1..TimelinePolicy.REBUILD_SENTINEL_TTL.seconds, "읽기가 센티널 키의 TTL 을 늘리지 않는다")
        store.pushHome(listOf(5L), e(40L))
        assertFalse(store.readHome(5L, null, 10).exists, "창 안의 push 가 붙어도 센티널이 있는 한 미구축")

        store.rebuildHome(5L, listOf(e(30L), e(20L)))

        assertEquals(listOf(40L, 30L, 20L), store.readHome(5L, null, 10).entries.map { it.postId })
        assertTrue(template.getExpire(TimelineKeys.home(5L)) > TimelinePolicy.REBUILD_SENTINEL_TTL.seconds)

        store.beginRebuildHome(6L)
        store.rebuildHome(6L, emptyList())
        assertFalse(store.readHome(6L, null, 10).exists)
    }

    @Test
    fun `ZSCORE 뒤에 다른 요청이 센티널을 놓아도 읽기는 미구축으로 본다`() {
        store.beginRebuildHome(7L)
        store.rebuildHome(7L, listOf(e(30L), e(20L)))
        val zset = spy(template.opsForZSet())
        val racing = spy(template)
        doReturn(zset).`when`(racing).opsForZSet()
        doAnswer {
            template.opsForZSet().addIfAbsent(TimelineKeys.home(7L), "-", -1.0)
            null
        }.`when`(zset).score(TimelineKeys.home(7L), "-")

        val slice = RedisTimelineStore(racing).readHome(7L, null, 10)

        assertFalse(slice.exists, "범위 결과에 센티널이 섞이면 파싱하지 않고 미구축으로 돌려준다")
        assertTrue(slice.entries.isEmpty())
    }

    @Test
    fun `작성자 타임라인은 별도 상한을 가진다`() {
        store.rebuildAuthor(7L, listOf(e(1L)))
        (2..TimelinePolicy.AUTHOR_TIMELINE_SIZE + 1).forEach { store.pushAuthor(7L, e(it.toLong())) }

        val entries = store.readAuthor(7L, null, TimelinePolicy.AUTHOR_TIMELINE_SIZE + 5).entries.map { it.postId }
        assertEquals(TimelinePolicy.AUTHOR_TIMELINE_SIZE, entries.size)
        assertEquals((TimelinePolicy.AUTHOR_TIMELINE_SIZE + 1).toLong(), entries.first())
        assertEquals(2L, entries.last())
    }

    @Test
    fun `팬아웃 큐는 소비자 그룹으로 읽고 ack 하지 않은 메시지는 유휴 시간이 지나면 다른 소비자가 가져간다`() {
        queue.enqueue(FanoutEvent(postId = 100L, authorId = 1L))
        queue.enqueue(FanoutEvent(postId = 101L, authorId = 1L))

        val first = queue.poll("a", 10, Duration.ofMillis(100))
        assertEquals(listOf(100L, 101L), first.map { it.event.postId })
        queue.ack(first[0].id)

        assertTrue(queue.poll("b", 10, Duration.ofMillis(100)).isEmpty())
        Thread.sleep(300)
        val claimed = queue.claimStale("b", Duration.ofMillis(200), 10)
        assertEquals(listOf(101L), claimed.map { it.event.postId })
        assertEquals(2L, claimed.single().deliveryCount)
        queue.ack(claimed.single().id)
        assertTrue(queue.claimStale("b", Duration.ofMillis(200), 10).isEmpty())
    }

    private fun e(id: Long) = TimelineEntry(id, id * 10)

    private class RedisContainer : GenericContainer<RedisContainer>("redis:7.4-alpine")

    private companion object {
        const val REDIS_PORT = 6379

        @Container
        @JvmField
        val redis = RedisContainer().withExposedPorts(REDIS_PORT)
    }
}

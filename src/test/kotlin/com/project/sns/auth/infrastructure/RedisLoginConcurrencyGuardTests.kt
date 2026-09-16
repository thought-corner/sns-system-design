package com.project.sns.auth.infrastructure

import com.project.sns.auth.application.SessionLimitExceededException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@Testcontainers(disabledWithoutDocker = true)
class RedisLoginConcurrencyGuardTests {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var guard: RedisLoginConcurrencyGuard

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(REDIS_PORT)).apply {
            afterPropertiesSet()
            start()
        }
        guard = RedisLoginConcurrencyGuard(StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() })
    }

    @AfterEach
    fun tearDown() {
        connectionFactory.destroy()
    }

    @Test
    fun `같은 사용자의 동시 로그인 잠금은 하나의 요청만 획득한다`() {
        val start = CountDownLatch(1)
        val successfulRequests = ConcurrentLinkedQueue<MockHttpServletRequest>()
        val rejectedCount = AtomicInteger()
        val executor = Executors.newFixedThreadPool(2)

        try {
            val attempts = List(2) {
                executor.submit {
                    val request = MockHttpServletRequest()
                    start.await()
                    try {
                        guard.acquireUntilRequestCompletion("user@example.com", request)
                        successfulRequests.add(request)
                    } catch (_: SessionLimitExceededException) {
                        rejectedCount.incrementAndGet()
                    }
                }
            }

            start.countDown()
            attempts.forEach { it.get(10, TimeUnit.SECONDS) }

            assertEquals(1, successfulRequests.size)
            assertEquals(1, rejectedCount.get())

            guard.release(successfulRequests.single())
            val nextRequest = MockHttpServletRequest()
            guard.acquireUntilRequestCompletion("user@example.com", nextRequest)
            guard.release(nextRequest)
        } finally {
            executor.shutdownNow()
        }
    }

    private class RedisContainer : GenericContainer<RedisContainer>("redis:7.4-alpine")

    private companion object {
        const val REDIS_PORT = 6379

        @Container
        @JvmField
        val redis = RedisContainer().withExposedPorts(REDIS_PORT)
    }
}

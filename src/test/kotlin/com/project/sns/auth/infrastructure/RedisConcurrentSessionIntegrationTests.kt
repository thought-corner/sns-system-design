package com.project.sns.auth.infrastructure

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.data.redis.RedisIndexedSessionRepository
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("redis-integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
class RedisConcurrentSessionIntegrationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var sessionRepository: RedisIndexedSessionRepository

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `동시 로그인에서도 Redis에 하나의 활성 세션만 생성한다`() {
        mockMvc.perform(
            post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(SIGN_UP_JSON),
        ).andExpect(status().isCreated)

        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val attempts = List(2) {
                executor.submit<Int> {
                    start.await()
                    mockMvc.perform(
                        post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_JSON),
                    ).andReturn().response.status
                }
            }

            start.countDown()
            val statuses = attempts.map { it.get(10, TimeUnit.SECONDS) }.sorted()

            assertEquals(listOf(200, 409), statuses)
            assertEquals(
                1,
                sessionRepository.findByIndexNameAndIndexValue(
                    FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                    EMAIL,
                ).size,
            )
            assertTrue(redisTemplate.keys("sns:session:login-lock:*").isEmpty())
        } finally {
            executor.shutdownNow()
        }
    }

    private class RedisContainer : GenericContainer<RedisContainer>("redis:7.4-alpine")

    private companion object {
        const val REDIS_PORT = 6379
        const val EMAIL = "redis-session-user@example.com"
        const val SIGN_UP_JSON =
            """{"email":"$EMAIL","password":"password123!","nickname":"RedisTester"}"""
        const val LOGIN_JSON = """{"email":"$EMAIL","password":"password123!"}"""

        @Container
        @JvmField
        val redis = RedisContainer().withExposedPorts(REDIS_PORT)

        @DynamicPropertySource
        @JvmStatic
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(REDIS_PORT) }
            registry.add("spring.data.redis.repositories.enabled") { false }
        }
    }
}

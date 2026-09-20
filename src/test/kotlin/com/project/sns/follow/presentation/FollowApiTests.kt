package com.project.sns.follow.presentation

import com.project.sns.PostgresTest
import com.project.sns.TestMediaStorageConfig
import com.project.sns.TestSessionConfig
import com.project.sns.TestTimelineConfig
import com.project.sns.follow.infrastructure.SpringDataFollowCountsJpaRepository
import com.project.sns.follow.infrastructure.SpringDataFollowJpaRepository
import com.project.sns.user.domain.User
import com.project.sns.user.infrastructure.SpringDataUserJpaRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 팔로우 API 계약 테스트. DB 는 `@PostgresTest` 가 띄운 공유 PostgreSQL 17 이라
 * 부분 유니크 인덱스·`ON CONFLICT` 중재·MVCC 동시성이 운영과 같은 조건에서 검증된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSessionConfig::class, TestMediaStorageConfig::class, TestTimelineConfig::class)
@PostgresTest
class FollowApiTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: SpringDataUserJpaRepository

    @Autowired
    private lateinit var followRepository: SpringDataFollowJpaRepository

    @Autowired
    private lateinit var followCountsRepository: SpringDataFollowCountsJpaRepository

    private lateinit var actor: User
    private lateinit var target: User

    @BeforeEach
    fun setUp() {
        followRepository.deleteAll()
        followCountsRepository.deleteAll()
        userRepository.deleteAll()
        actor = userRepository.saveAndFlush(user(ACTOR_EMAIL, "행위자"))
        target = userRepository.saveAndFlush(user(TARGET_EMAIL, "대상자"))
    }

    @Test
    fun `팔로우와 언팔로우는 멱등하며 삭제 이력과 집계에 반영된다`() {
        repeat(2) {
            follow(target.id!!).andExpect(status().isNoContent)
        }

        mockMvc.perform(get("/api/users/${target.id}/follow-stats").with(asActor()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(target.id!!))
            .andExpect(jsonPath("$.followerCount").value(1))
            .andExpect(jsonPath("$.followingCount").value(0))

        mockMvc.perform(get("/api/users/${actor.id}/follow-stats").with(asActor()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.followerCount").value(0))
            .andExpect(jsonPath("$.followingCount").value(1))

        repeat(2) {
            unfollow(target.id!!).andExpect(status().isNoContent)
        }

        assertEquals(1L, followRepository.count())
        assertNotNull(followRepository.findAll().single().deletedAt)
        assertEquals(0L, followRepository.countFollowers(target.id!!))
        assertEquals(0L, followRepository.countFollowing(actor.id!!))
        assertCountsMatchRelations(actor.id!!, target.id!!)

        follow(target.id!!).andExpect(status().isNoContent)

        val histories = followRepository.findAll().sortedBy { it.id }
        assertEquals(2, histories.size)
        assertNotNull(histories[0].deletedAt)
        assertNull(histories[1].deletedAt)
        assertEquals(1L, followRepository.countFollowers(target.id!!))
        assertEquals(1L, followRepository.countFollowing(actor.id!!))
        assertCountsMatchRelations(actor.id!!, target.id!!)

        mockMvc.perform(get("/api/users/${target.id}/follow-stats").with(asActor()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.followerCount").value(1))
            .andExpect(jsonPath("$.followingCount").value(0))
    }

    @Test
    fun `동시에 팔로우해도 관계는 하나만 생성된다`() {
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)

        try {
            val attempts = List(CONCURRENT_REQUESTS) {
                executor.submit<Int> {
                    start.await()
                    follow(target.id!!).andReturn().response.status
                }
            }

            start.countDown()
            val statuses = attempts.map { it.get(10, TimeUnit.SECONDS) }

            assertEquals(List(CONCURRENT_REQUESTS) { 204 }, statuses)
            assertEquals(1L, followRepository.count())
            assertEquals(1L, followRepository.countFollowers(target.id!!))
            assertCountsMatchRelations(actor.id!!, target.id!!)
        } finally {
            executor.shutdownNow()
        }
    }

    // 인기 사용자의 카운터 행(핫 로우)에 서로 다른 행위자의 증감이 몰리고, 맞팔로우가 교차해 잠금 순서가 엇갈리는 상황.
    @Test
    fun `서로 다른 사용자들이 동시에 팔로우하고 맞팔로우해도 카운터는 관계 수와 같다`() {
        val followers =
            (1..CONCURRENT_REQUESTS).map { userRepository.saveAndFlush(user("follower$it@example.com", "팔로워$it")) }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS * 2)

        try {
            val attempts = followers.flatMap { follower ->
                listOf(
                    executor.submit<Int> {
                        start.await()
                        mockMvc.perform(
                            put("/api/users/${target.id}/follow").with(user(follower.id.toString())).with(csrf())
                        )
                            .andReturn().response.status
                    },
                    executor.submit<Int> {
                        start.await()
                        mockMvc.perform(
                            put("/api/users/${follower.id}/follow").with(user(target.id.toString())).with(csrf())
                        )
                            .andReturn().response.status
                    },
                )
            }

            start.countDown()
            val statuses = attempts.map { it.get(20, TimeUnit.SECONDS) }

            assertEquals(List(CONCURRENT_REQUESTS * 2) { 204 }, statuses)
            assertCountsMatchRelations(target.id!!)
            assertEquals(CONCURRENT_REQUESTS.toLong(), followCountsRepository.findById(target.id!!).get().followerCount)
            assertEquals(
                CONCURRENT_REQUESTS.toLong(),
                followCountsRepository.findById(target.id!!).get().followingCount
            )
            followers.forEach { assertCountsMatchRelations(it.id!!) }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `자기 자신 팔로우와 존재하지 않는 대상은 오류로 응답한다`() {
        follow(actor.id!!)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("SELF_FOLLOW_NOT_ALLOWED"))

        follow(Long.MAX_VALUE)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))

        assertEquals(0L, followRepository.count())
    }

    @Test
    fun `자기 자신 언팔로우와 존재하지 않는 대상 통계는 오류로 응답한다`() {
        unfollow(actor.id!!)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("SELF_FOLLOW_NOT_ALLOWED"))

        mockMvc.perform(get("/api/users/${Long.MAX_VALUE}/follow-stats").with(asActor()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
    }

    @Test
    fun `인증 없이 팔로우 API 를 사용할 수 없다`() {
        mockMvc.perform(put("/api/users/${target.id}/follow").with(csrf()))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(get("/api/users/${target.id}/follow-stats"))
            .andExpect(status().isUnauthorized)

        assertEquals(0L, followRepository.count())
    }

    @Test
    fun `principal 이 사용자 ID 가 아닌 세션은 팔로우할 수 없다`() {
        mockMvc.perform(put("/api/users/${target.id}/follow").with(user(ACTOR_EMAIL)).with(csrf()))
            .andExpect(status().isUnauthorized)

        assertEquals(0L, followRepository.count())
    }

    @Test
    fun `CSRF 토큰 없이 팔로우 관계를 변경할 수 없다`() {
        mockMvc.perform(put("/api/users/${target.id}/follow").with(asActor()))
            .andExpect(status().isForbidden)

        mockMvc.perform(delete("/api/users/${target.id}/follow").with(asActor()))
            .andExpect(status().isForbidden)

        assertEquals(0L, followRepository.count())
    }

    private fun follow(followingId: Long) = mockMvc.perform(
        put("/api/users/$followingId/follow")
            .with(asActor())
            .with(csrf()),
    )

    private fun unfollow(followingId: Long) = mockMvc.perform(
        delete("/api/users/$followingId/follow")
            .with(asActor())
            .with(csrf()),
    )

    // principal 의 username 은 users.id — 이메일이 아니다(AuthenticationExtensions.userIdOrNull).
    private fun asActor() = user(actor.id.toString())

    // 카운터(follow_counts)는 활성 관계의 COUNT 와 항상 같아야 한다 — 행이 없으면 0.
    private fun assertCountsMatchRelations(vararg userIds: Long) {
        userIds.forEach { userId ->
            val counts = followCountsRepository.findById(userId).orElse(null)
            assertEquals(
                followRepository.countFollowers(userId),
                counts?.followerCount ?: 0L,
                "followerCount of $userId"
            )
            assertEquals(
                followRepository.countFollowing(userId),
                counts?.followingCount ?: 0L,
                "followingCount of $userId"
            )
        }
    }

    private fun user(email: String, nickname: String) = User(
        email = email,
        passwordHash = "encoded",
        nickname = nickname,
    )

    private companion object {
        const val CONCURRENT_REQUESTS = 8
        const val ACTOR_EMAIL = "actor@example.com"
        const val TARGET_EMAIL = "target@example.com"
    }
}

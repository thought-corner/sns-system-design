package com.project.sns.timeline.presentation

import com.project.sns.InMemoryFanoutQueue
import com.project.sns.InMemoryFollowCache
import com.project.sns.InMemoryPostCache
import com.project.sns.InMemoryTimelineStore
import com.project.sns.PostgresTest
import com.project.sns.TestMediaStorageConfig
import com.project.sns.TestSessionConfig
import com.project.sns.TestTimelineConfig
import com.project.sns.follow.infrastructure.SpringDataFollowCountsJpaRepository
import com.project.sns.follow.infrastructure.SpringDataFollowJpaRepository
import com.project.sns.media.infrastructure.SpringDataMediaJpaRepository
import com.project.sns.post.infrastructure.SpringDataPostCountsJpaRepository
import com.project.sns.post.infrastructure.SpringDataPostJpaRepository
import com.project.sns.timeline.application.TimelineFanoutService
import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.timeline.infrastructure.SpringDataTimelineQueryRepository
import com.project.sns.user.domain.User
import com.project.sns.user.infrastructure.SpringDataUserJpaRepository
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSessionConfig::class, TestMediaStorageConfig::class, TestTimelineConfig::class)
@PostgresTest
class TimelineApiTests {
    @Autowired
    private lateinit var mockMvc: MockMvc
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    @Autowired
    private lateinit var userRepository: SpringDataUserJpaRepository
    @Autowired
    private lateinit var postRepository: SpringDataPostJpaRepository
    @Autowired
    private lateinit var postCountsRepository: SpringDataPostCountsJpaRepository
    @Autowired
    private lateinit var mediaRepository: SpringDataMediaJpaRepository
    @Autowired
    private lateinit var followRepository: SpringDataFollowJpaRepository
    @Autowired
    private lateinit var followCountsRepository: SpringDataFollowCountsJpaRepository
    @Autowired
    private lateinit var timelineStore: InMemoryTimelineStore
    @Autowired
    private lateinit var fanoutQueue: InMemoryFanoutQueue
    @Autowired
    private lateinit var followCache: InMemoryFollowCache
    @Autowired
    private lateinit var postCache: InMemoryPostCache
    @Autowired
    private lateinit var fanoutService: TimelineFanoutService
    @Autowired
    private lateinit var queryRepository: SpringDataTimelineQueryRepository
    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var me: User
    private lateinit var alice: User
    private lateinit var bob: User

    @BeforeEach
    fun setUp() {
        timelineStore.clear()
        fanoutQueue.clear()
        followCache.clear()
        postCache.clear()
        mediaRepository.deleteAllInBatch()
        postCountsRepository.deleteAll()
        postRepository.deleteAllInBatch()
        followCountsRepository.deleteAll()
        followRepository.deleteAll()
        userRepository.deleteAll()
        me = userRepository.saveAndFlush(user("me@example.com", "나"))
        alice = userRepository.saveAndFlush(user("alice@example.com", "앨리스"))
        bob = userRepository.saveAndFlush(user("bob@example.com", "밥"))
    }

    @Test
    fun `팔로우한 사람과 나의 글이 팬아웃되어 최신순으로 읽히고 답글은 실리지 않는다`() {
        follow(me, alice)
        val a1 = createPost(alice, "앨리스 1")
        val mine = createPost(me, "내 글")
        val a2 = createPost(alice, "앨리스 2")
        createPost(bob, "밥 — 팔로우 안 함")
        mockMvc.perform(contentRequest(post("/api/posts/$a1/replies"), alice, "답글")).andExpect(status().isCreated)
        drainFanout()

        val page = read(me)
        assertEquals(listOf(a2, mine, a1), page["items"].let(::ids))
        assertTrue(page["nextCursor"].isNull)
        assertEquals(listOf(a2, mine, a1), timelineStore.homeIds(me.id!!))
    }

    @Test
    fun `커서로 이어 읽고 삭제된 글은 빠지며 리포스트 행은 원본을 함께 싣는다`() {
        follow(me, alice)
        val ids = (1..5).map { createPost(alice, "글 $it") }
        mockMvc.perform(put("/api/posts/${ids[0]}/repost").with(asUser(bob)).with(csrf()))
            .andExpect(status().isNoContent)
        follow(me, bob)
        drainFanout()
        val repostRowId = postRepository.findAll().single { it.repostOfId == ids[0] }.id!!

        val first = read(me, limit = 2)
        assertEquals(listOf(repostRowId, ids[4]), first["items"].let(::ids))
        assertEquals(ids[0], first["items"][0]["original"]["id"].asLong())
        assertEquals("", first["items"][0]["post"]["content"].asText())
        assertTrue(first["items"][1]["original"].isNull)
        assertEquals(ids[4], first["nextCursor"].asLong())

        mockMvc.perform(delete("/api/posts/${ids[3]}").with(asUser(alice)).with(csrf())).andExpect(status().isNoContent)
        val second = read(me, cursor = first["nextCursor"].asLong(), limit = 2)
        assertEquals(listOf(ids[2], ids[1]), second["items"].let(::ids))
        assertEquals(ids[1], second["nextCursor"].asLong())

        val third = read(me, cursor = second["nextCursor"].asLong(), limit = 2)
        assertEquals(listOf(ids[0]), third["items"].let(::ids))
        assertTrue(third["nextCursor"].isNull)
    }

    @Test
    fun `키가 없으면 재구축으로 응답하고 이후 팬아웃이 이어진다`() {
        follow(me, alice)
        val a1 = createPost(alice, "재구축 전")
        drainFanout()
        timelineStore.clear()

        assertEquals(listOf(a1), read(me)["items"].let(::ids))
        assertEquals(listOf(a1), timelineStore.homeIds(me.id!!))

        val a2 = createPost(alice, "재구축 후")
        drainFanout()
        assertEquals(listOf(a2, a1), read(me)["items"].let(::ids))
    }

    @Test
    fun `키가 없는 사용자에게 팬아웃이 먼저 와도 다음 읽기는 재구축이라 이전 글까지 보인다`() {
        follow(me, alice)
        val a1 = createPost(alice, "이전 글")
        drainFanout()
        read(me)
        timelineStore.clear()

        val a2 = createPost(alice, "키 없는 동안의 글")
        drainFanout()
        assertTrue(timelineStore.homeIds(me.id!!).isNullOrEmpty(), "키가 없으면 팬아웃은 키를 만들지 않는다")

        assertEquals(listOf(a2, a1), read(me)["items"].let(::ids))
        val a3 = createPost(alice, "키 생긴 뒤의 글")
        drainFanout()
        assertEquals(listOf(a3, a2, a1), timelineStore.homeIds(me.id!!))
    }

    @Test
    fun `리포스트를 취소하면 타임라인에서 바로 빠진다`() {
        follow(me, bob)
        val a1 = createPost(alice, "원본")
        mockMvc.perform(put("/api/posts/$a1/repost").with(asUser(bob)).with(csrf())).andExpect(status().isNoContent)
        drainFanout()
        val repostRowId = postRepository.findAll().single { it.repostOfId == a1 }.id!!
        assertEquals(listOf(repostRowId), read(me)["items"].let(::ids))
        assertTrue(postCache.contains(repostRowId))

        mockMvc.perform(delete("/api/posts/$a1/repost").with(asUser(bob)).with(csrf())).andExpect(status().isNoContent)

        assertFalse(postCache.contains(repostRowId))
        assertEquals(emptyList<Long>(), read(me)["items"].let(::ids))
    }

    @Test
    fun `팔로우하면 최근 글이 백필되고 언팔로우하면 사라진다`() {
        val a1 = createPost(alice, "옛 글 1")
        val a2 = createPost(alice, "옛 글 2")
        val a3 = createPost(alice, "옛 글 3")
        drainFanout()
        read(me)
        assertTrue(timelineStore.homeIds(me.id!!).isNullOrEmpty(), "팔로우 전엔 후보가 없어 홈 키가 만들어지지 않는다")

        follow(me, alice)
        assertEquals(listOf(a3, a2, a1), read(me)["items"].let(::ids))

        mockMvc.perform(delete("/api/users/${alice.id}/follow").with(asUser(me)).with(csrf()))
            .andExpect(status().isNoContent)
        assertEquals(emptyList<Long>(), read(me)["items"].let(::ids))
    }

    @Test
    fun `이미 조회 기록을 남긴 글은 타임라인에서 빠진다`() {
        follow(me, alice)
        val a1 = createPost(alice, "읽은 글")
        val a2 = createPost(alice, "안 읽은 글")
        drainFanout()
        mockMvc.perform(post("/api/posts/$a1/views").with(asUser(me)).with(csrf())).andExpect(status().isNoContent)

        assertEquals(listOf(a2), read(me)["items"].let(::ids))
    }

    @Test
    fun `대형 계정·인기 글 native 쿼리는 실 PostgreSQL 에서 실행된다`() {
        follow(me, alice)
        val b1 = createPost(bob, "밥의 글")
        mockMvc.perform(put("/api/posts/$b1/like").with(asUser(alice)).with(csrf())).andExpect(status().isNoContent)

        assertEquals(emptyList<Long>(), queryRepository.findFollowedCelebrityIds(me.id!!, TimelinePolicy.CELEBRITY_MAX_FOLLOWERS))
        assertEquals(listOf(alice.id!!), queryRepository.findFollowedCelebrityIds(me.id!!, 0L))
        assertEquals(listOf(b1), queryRepository.findPopularPostIds(java.time.Instant.now().minusSeconds(60), 1L, null, 10))
        assertEquals(emptyList<Long>(), queryRepository.findPopularPostIds(java.time.Instant.now().minusSeconds(60), 1L, b1, 10))
        assertEquals(emptyList<Long>(), queryRepository.findPopularPostIds(java.time.Instant.now().minusSeconds(60), TimelinePolicy.POPULAR_MIN_LIKES, null, 10))
    }

    @Test
    fun `팔로우 캐시는 재구축 때 채워지고 팔로우 변경을 따라가며 게시글 캐시는 삭제 시 비워진다`() {
        follow(me, alice)
        val a1 = createPost(alice, "글")
        drainFanout()
        timelineStore.clear()
        assertFalse(followCache.hasFollowing(me.id!!))

        assertEquals(listOf(a1), read(me)["items"].let(::ids))
        assertEquals(setOf(alice.id!!), followCache.followingIds(me.id!!))
        assertTrue(postCache.contains(a1), "hydrate 가 게시글 스냅샷을 캐시에 넣는다")

        follow(me, bob)
        assertFalse(followCache.hasFollowing(me.id!!), "팔로우 변경은 팔로잉 캐시를 비운다")
        timelineStore.clear()
        read(me)
        assertEquals(setOf(alice.id!!, bob.id!!), followCache.followingIds(me.id!!))
        mockMvc.perform(delete("/api/users/${bob.id}/follow").with(asUser(me)).with(csrf())).andExpect(status().isNoContent)
        assertFalse(followCache.hasFollowing(me.id!!))

        mockMvc.perform(delete("/api/posts/$a1").with(asUser(alice)).with(csrf())).andExpect(status().isNoContent)
        assertFalse(postCache.contains(a1), "삭제가 커밋되면 캐시에서 빠진다")
        assertEquals(emptyList<Long>(), read(me)["items"].let(::ids))
    }

    @Test
    fun `evict 를 놓쳐 캐시에 스냅샷이 남아 있어도 삭제된 글은 타임라인에 나오지 않는다`() {
        follow(me, alice)
        val a1 = createPost(alice, "글")
        drainFanout()
        assertEquals(listOf(a1), read(me)["items"].let(::ids))
        assertTrue(postCache.contains(a1))

        transactionTemplate.execute { postRepository.softDeleteById(a1) }

        assertTrue(postCache.contains(a1), "저장소를 직접 지워 evict 가 없는 상황")
        assertEquals(emptyList<Long>(), read(me)["items"].let(::ids))
    }

    @Test
    fun `인증 없이는 읽을 수 없고 limit 는 상한으로 잘린다`() {
        mockMvc.perform(get("/api/timeline")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/timeline").param("limit", "999").with(asUser(me)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray)
    }

    private fun ids(items: JsonNode): List<Long> = items.toList().map { it["post"]["id"].asLong() }

    private fun drainFanout() {
        fanoutQueue.poll("test", Int.MAX_VALUE, Duration.ZERO).forEach {
            fanoutService.fanout(it.event)
            fanoutQueue.ack(it.id)
        }
        assertEquals(0, fanoutQueue.unackedCount())
    }

    private fun read(user: User, cursor: Long? = null, limit: Int = 20): JsonNode {
        val request = get("/api/timeline").param("limit", limit.toString()).with(asUser(user))
        cursor?.let { request.param("cursor", it.toString()) }
        return objectMapper.readTree(
            mockMvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString
        )
    }

    private fun follow(follower: User, following: User) {
        mockMvc.perform(put("/api/users/${following.id}/follow").with(asUser(follower)).with(csrf()))
            .andExpect(status().isNoContent)
    }

    private fun createPost(author: User, content: String): Long =
        mockMvc.perform(contentRequest(post("/api/posts"), author, content))
            .andExpect(status().isCreated)
            .andReturn().let { objectMapper.readTree(it.response.contentAsString)["id"].asLong() }

    private fun contentRequest(
        builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder,
        user: User,
        content: String
    ) =
        builder.with(asUser(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("content" to content)))

    private fun asUser(user: User) = user(user.id.toString())

    private fun user(email: String, nickname: String) =
        User(email = email, passwordHash = "encoded", nickname = nickname)
}

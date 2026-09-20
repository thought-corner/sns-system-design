package com.project.sns.media.presentation

import com.project.sns.InMemoryMediaStorage
import com.project.sns.PostgresTest
import com.project.sns.TestMediaStorageConfig
import com.project.sns.TestSessionConfig
import com.project.sns.TestTimelineConfig
import com.project.sns.media.TestImages
import com.project.sns.media.domain.MediaStatus
import com.project.sns.media.infrastructure.SpringDataMediaJpaRepository
import com.project.sns.post.infrastructure.SpringDataPostCountsJpaRepository
import com.project.sns.post.infrastructure.SpringDataPostJpaRepository
import com.project.sns.user.domain.User
import com.project.sns.user.infrastructure.SpringDataUserJpaRepository
import tools.jackson.databind.ObjectMapper
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSessionConfig::class, TestMediaStorageConfig::class, TestTimelineConfig::class)
@PostgresTest
class MediaApiTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: SpringDataUserJpaRepository

    @Autowired
    private lateinit var mediaRepository: SpringDataMediaJpaRepository

    @Autowired
    private lateinit var postRepository: SpringDataPostJpaRepository

    @Autowired
    private lateinit var postCountsRepository: SpringDataPostCountsJpaRepository

    @Autowired
    private lateinit var storage: InMemoryMediaStorage

    private lateinit var owner: User
    private lateinit var other: User

    @BeforeEach
    fun setUp() {
        storage.clear()
        mediaRepository.deleteAllInBatch()
        postCountsRepository.deleteAll()
        postRepository.deleteAllInBatch()
        userRepository.deleteAll()
        owner = userRepository.saveAndFlush(user("owner@example.com", "소유자"))
        other = userRepository.saveAndFlush(user("other@example.com", "타인"))
    }

    @Test
    fun `발급·업로드·완료·첨부·조회 전 과정`() {
        val png = TestImages.png(2, 3)
        val mediaId = initiate(owner, "image/png", png.size.toLong())
        val key = mediaRepository.findById(mediaId).get().storageKey
        assertTrue(key.startsWith("media/${owner.id}/") && key.endsWith(".png"))

        storage.put(key, "image/png", png)
        mockMvc.perform(post("/api/media/$mediaId/complete").with(asUser(owner)).with(csrf()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(mediaId))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.contentType").value("image/png"))
            .andExpect(jsonPath("$.sizeBytes").value(png.size))
            .andExpect(jsonPath("$.width").value(2))
            .andExpect(jsonPath("$.height").value(3))
        mockMvc.perform(post("/api/media/$mediaId/complete").with(asUser(owner)).with(csrf()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("READY"))

        val secondId = readyMedia(owner, TestImages.jpeg(4, 2), "image/jpeg")
        val postId = mockMvc.perform(postRequest("/api/posts", owner, "본문", listOf(secondId, mediaId)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.media.length()").value(2))
            .andExpect(jsonPath("$.media[0].id").value(secondId))
            .andExpect(jsonPath("$.media[1].id").value(mediaId))
            .andReturn().let { objectMapper.readTree(it.response.contentAsString)["id"].asLong() }

        mockMvc.perform(get("/api/posts/$postId").with(asUser(other)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.media[0].id").value(secondId))
            .andExpect(jsonPath("$.media[0].contentType").value("image/jpeg"))
            .andExpect(jsonPath("$.media[0].width").value(4))
            .andExpect(
                jsonPath("$.media[0].url").value(
                    "https://media.test/download/${
                        mediaRepository.findById(secondId).get().storageKey
                    }"
                )
            )
            .andExpect(jsonPath("$.media[1].id").value(mediaId))
            .andExpect(jsonPath("$.media[1].url").isNotEmpty)

        val rows = mediaRepository.findAll().sortedBy { it.position }
        assertEquals(listOf(secondId to 0, mediaId to 1), rows.map { it.id to it.position })
        assertTrue(rows.all { it.postId == postId })
    }

    @Test
    fun `미디어 없는 게시글은 media 가 빈 목록이고 리포스트 행도 빈 목록이다`() {
        val mediaId = readyMedia(owner, TestImages.png(), "image/png")
        val postId = mockMvc.perform(postRequest("/api/posts", owner, "본문", listOf(mediaId)))
            .andExpect(status().isCreated).andReturn()
            .let { objectMapper.readTree(it.response.contentAsString)["id"].asLong() }
        val plainId = mockMvc.perform(postRequest("/api/posts", owner, "본문만", emptyList()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.media").isEmpty)
            .andReturn().let { objectMapper.readTree(it.response.contentAsString)["id"].asLong() }
        mockMvc.perform(get("/api/posts/$plainId").with(asUser(owner))).andExpect(jsonPath("$.media").isEmpty)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/posts/$postId/repost")
                .with(asUser(other)).with(csrf())
        )
            .andExpect(status().isNoContent)
        val repostRowId = postRepository.findAll().single { it.repostOfId == postId }.id!!
        mockMvc.perform(get("/api/posts/$repostRowId").with(asUser(owner)))
            .andExpect(jsonPath("$.media").isEmpty)
        mockMvc.perform(get("/api/posts/$postId").with(asUser(owner)))
            .andExpect(jsonPath("$.media.length()").value(1))
    }

    @Test
    fun `답글과 인용에도 미디어를 붙일 수 있다`() {
        val originId = mockMvc.perform(postRequest("/api/posts", owner, "원본", emptyList()))
            .andExpect(status().isCreated).andReturn()
            .let { objectMapper.readTree(it.response.contentAsString)["id"].asLong() }
        val replyMedia = readyMedia(other, TestImages.png(), "image/png")
        val quoteMedia = readyMedia(other, TestImages.gif(), "image/gif")

        mockMvc.perform(postRequest("/api/posts/$originId/replies", other, "답글", listOf(replyMedia)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.parentPostId").value(originId))
            .andExpect(jsonPath("$.media[0].id").value(replyMedia))
        mockMvc.perform(postRequest("/api/posts/$originId/quotes", other, "인용", listOf(quoteMedia)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.quotedPostId").value(originId))
            .andExpect(jsonPath("$.media[0].contentType").value("image/gif"))
    }

    @Test
    fun `발급 검증 — 형식·크기·본문 누락`() {
        mockMvc.perform(initiateRequest(owner, """{"contentType":"image/svg+xml","sizeBytes":10}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MEDIA_TYPE_NOT_ALLOWED"))
        mockMvc.perform(initiateRequest(owner, """{"contentType":"image/png","sizeBytes":10485761}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MEDIA_TOO_LARGE"))
        mockMvc.perform(initiateRequest(owner, """{"contentType":"image/png","sizeBytes":0}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.fieldErrors.sizeBytes").isNotEmpty)
        mockMvc.perform(initiateRequest(owner, """{"sizeBytes":10}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.contentType").isNotEmpty)
        assertEquals(0L, mediaRepository.count())
    }

    @Test
    fun `완료 검증 — 미업로드는 409 로 남고 불일치는 폐기되어 400 이다`() {
        val png = TestImages.png()
        val notUploaded = initiate(owner, "image/png", png.size.toLong())
        mockMvc.perform(post("/api/media/$notUploaded/complete").with(asUser(owner)).with(csrf()))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_UPLOADED"))
        assertEquals(MediaStatus.PENDING, mediaRepository.findById(notUploaded).get().status)
        assertNull(mediaRepository.findById(notUploaded).get().deletedAt)

        val mismatch = initiate(owner, "image/png", png.size.toLong())
        val mismatchKey = mediaRepository.findById(mismatch).get().storageKey
        storage.put(mismatchKey, "image/png", TestImages.jpeg().copyOf(png.size))
        mockMvc.perform(post("/api/media/$mismatch/complete").with(asUser(owner)).with(csrf()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MEDIA_INVALID"))
        assertFalse(storage.contains(mismatchKey), "객체가 폐기된다")
        assertNotNull(mediaRepository.findById(mismatch).get().deletedAt, "행은 소프트 삭제된다")
        mockMvc.perform(post("/api/media/$mismatch/complete").with(asUser(owner)).with(csrf()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"))

        val oversize = initiate(owner, "image/png", 10L)
        storage.put(mediaRepository.findById(oversize).get().storageKey, "image/png", png)
        mockMvc.perform(post("/api/media/$oversize/complete").with(asUser(owner)).with(csrf()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MEDIA_INVALID"))

        val mine = initiate(owner, "image/png", png.size.toLong())
        mockMvc.perform(post("/api/media/$mine/complete").with(asUser(other)).with(csrf()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"))
        mockMvc.perform(post("/api/media/${Long.MAX_VALUE}/complete").with(asUser(owner)).with(csrf()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `첨부 검증 — 남의 것·완료 전·이미 첨부·개수·중복은 게시글을 만들지 않는다`() {
        val ready = readyMedia(owner, TestImages.png(), "image/png")
        val pending = initiate(owner, "image/png", 10L)
        val othersReady = readyMedia(other, TestImages.png(), "image/png")

        mockMvc.perform(postRequest("/api/posts", owner, "본문", listOf(othersReady)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"))
        mockMvc.perform(postRequest("/api/posts", owner, "본문", listOf(pending)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_READY"))
        mockMvc.perform(postRequest("/api/posts", owner, "본문", listOf(ready, ready)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.fieldErrors.mediaIds").isNotEmpty)
        val five = List(5) { readyMedia(owner, TestImages.png(), "image/png") }
        mockMvc.perform(postRequest("/api/posts", owner, "본문", five))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.mediaIds").isNotEmpty)
        mockMvc.perform(
            post("/api/posts").with(asUser(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"content":"본문","mediaIds":[null]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.fieldErrors.mediaIds").isNotEmpty)
        assertEquals(0L, postRepository.count(), "실패한 요청은 게시글을 남기지 않는다")
        assertTrue(mediaRepository.findAll().all { it.postId == null }, "첨부도 남지 않는다")

        mockMvc.perform(postRequest("/api/posts", owner, "본문", listOf(ready, pending)))
            .andExpect(status().isConflict)
        assertNull(mediaRepository.findById(ready).get().postId)
        assertEquals(0L, postRepository.count())

        mockMvc.perform(postRequest("/api/posts", owner, "본문", listOf(ready))).andExpect(status().isCreated)
        mockMvc.perform(postRequest("/api/posts", owner, "두 번째", listOf(ready)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MEDIA_ALREADY_ATTACHED"))
        assertEquals(1L, postRepository.count())
    }

    @Test
    fun `인증 없이 발급할 수 없고 CSRF 없이 완료할 수 없다`() {
        mockMvc.perform(
            post("/api/media/uploads").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"contentType":"image/png","sizeBytes":10}""")
        )
            .andExpect(status().isUnauthorized)
        mockMvc.perform(post("/api/media/1/complete").with(asUser(owner))).andExpect(status().isForbidden)
        assertEquals(0L, mediaRepository.count())
    }

    private fun initiate(user: User, contentType: String, sizeBytes: Long): Long = mockMvc.perform(
        initiateRequest(
            user,
            objectMapper.writeValueAsString(mapOf("contentType" to contentType, "sizeBytes" to sizeBytes))
        ),
    )
        .andExpect(status().isCreated)
        .andExpect(jsonPath("$.uploadUrl").isNotEmpty)
        .andExpect(jsonPath("$.expiresAt").isNotEmpty)
        .andReturn().let { objectMapper.readTree(it.response.contentAsString)["mediaId"].asLong() }

    private fun readyMedia(user: User, bytes: ByteArray, contentType: String): Long {
        val id = initiate(user, contentType, bytes.size.toLong())
        storage.put(mediaRepository.findById(id).get().storageKey, contentType, bytes)
        mockMvc.perform(post("/api/media/$id/complete").with(asUser(user)).with(csrf())).andExpect(status().isOk)
        return id
    }

    private fun initiateRequest(user: User, body: String) =
        post("/api/media/uploads").with(asUser(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)

    private fun postRequest(path: String, user: User, content: String, mediaIds: List<Long>) =
        post(path).with(asUser(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("content" to content, "mediaIds" to mediaIds)))

    private fun asUser(user: User) = user(user.id.toString())

    private fun user(email: String, nickname: String) =
        User(email = email, passwordHash = "encoded", nickname = nickname)
}

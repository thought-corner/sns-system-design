package com.project.sns.post.presentation

import com.project.sns.PostgresTest
import com.project.sns.TestMediaStorageConfig
import com.project.sns.TestSessionConfig
import com.project.sns.TestTimelineConfig
import com.project.sns.post.domain.Post
import com.project.sns.post.infrastructure.SpringDataPostCountsJpaRepository
import com.project.sns.post.infrastructure.SpringDataPostJpaRepository
import com.project.sns.post.infrastructure.SpringDataPostLikeJpaRepository
import com.project.sns.post.infrastructure.SpringDataPostViewJpaRepository
import com.project.sns.user.domain.User
import com.project.sns.user.infrastructure.SpringDataUserJpaRepository
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSessionConfig::class, TestMediaStorageConfig::class, TestTimelineConfig::class)
@PostgresTest
class PostApiTests {
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
    private lateinit var postLikeRepository: SpringDataPostLikeJpaRepository

    @Autowired
    private lateinit var postViewRepository: SpringDataPostViewJpaRepository

    private lateinit var author: User
    private lateinit var reader: User

    @BeforeEach
    fun setUp() {
        postViewRepository.deleteAll()
        postLikeRepository.deleteAll()
        postCountsRepository.deleteAll()
        postRepository.deleteAllInBatch()
        userRepository.deleteAll()
        author = userRepository.saveAndFlush(user("author@example.com", "작성자"))
        reader = userRepository.saveAndFlush(user("reader@example.com", "독자"))
    }

    @Test
    fun `게시글을 작성하고 조회하면 통계는 0 으로 시작한다`() {
        val postId = createPost(author, "첫 게시글")

        mockMvc.perform(get("/api/posts/$postId").with(asUser(reader)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(postId))
            .andExpect(jsonPath("$.authorId").value(author.id!!))
            .andExpect(jsonPath("$.content").value("첫 게시글"))
            .andExpect(jsonPath("$.parentPostId").doesNotExist())
            .andExpect(jsonPath("$.quotedPostId").doesNotExist())
            .andExpect(jsonPath("$.repostOfId").doesNotExist())
            .andExpect(jsonPath("$.createdAt").isNotEmpty)
            .andExpect(jsonPath("$.counts.replyCount").value(0))
            .andExpect(jsonPath("$.counts.viewCount").value(0))
    }

    @Test
    fun `본문이 공백뿐이거나 없거나 500자를 넘으면 400 이다`() {
        listOf(
            """{"content":"   "}""",
            """{"content":"\u3000\u00a0\u3000"}""",
            """{}""",
            """{"content":"a\u0000b"}""",
            """{"content":"a\ud800b"}""",
            objectMapper.writeValueAsString(mapOf("content" to "a".repeat(501))),
            objectMapper.writeValueAsString(mapOf("content" to "😀".repeat(501))),
        ).forEach { body ->
            val response = mockMvc.perform(
                post("/api/posts").with(asUser(author)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andReturn().response
            assertEquals(400, response.status, "body=$body")
            val error = objectMapper.readTree(response.contentAsString)
            assertEquals("INVALID_REQUEST", error["code"]?.asText(), "body=$body")
            assertNotNull(error["fieldErrors"]?.get("content"), "body=$body")
        }
        assertEquals(0L, postRepository.count())

        val originId = createPost(author, "원본")
        mockMvc.perform(contentRequest(post("/api/posts/$originId/replies"), reader, " "))
            .andExpect(status().isBadRequest)
        mockMvc.perform(contentRequest(post("/api/posts/$originId/quotes"), reader, " "))
            .andExpect(status().isBadRequest)
        assertEquals(1L, postRepository.count())
        assertCountsMatchRelations(originId)
    }

    @Test
    fun `본문은 앞뒤 공백을 제거해 저장하며 제거 후 500자는 허용된다`() {
        val postId = mockMvc.perform(contentRequest(post("/api/posts"), author, "\u3000 본문 \n"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.authorId").value(author.id!!))
            .andExpect(jsonPath("$.content").value("본문"))
            .andExpect(jsonPath("$.counts.replyCount").value(0))
            .andExpect(jsonPath("$.counts.likeCount").value(0))
            .andExpect(jsonPath("$.counts.viewCount").value(0))
            .andReturn().let { objectMapper.readTree(it.response.contentAsString)["id"].asLong() }
        mockMvc.perform(get("/api/posts/$postId").with(asUser(reader)))
            .andExpect(jsonPath("$.content").value("본문"))

        createPost(author, " " + "a".repeat(500) + " ")
        createPost(author, "😀".repeat(500))
        mockMvc.perform(contentRequest(post("/api/posts"), author, "😀 본문"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.content").value("😀 본문"))
    }

    @Test
    fun `답글과 인용은 원본의 답글 수·인용 수에 반영되고 삭제하면 되돌아간다`() {
        val originId = createPost(author, "원본")

        val replyId = mockMvc.perform(contentRequest(post("/api/posts/$originId/replies"), reader, "답글"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.parentPostId").value(originId))
            .andReturn().let { objectMapper.readTree(it.response.contentAsString)["id"].asLong() }
        val quoteId = mockMvc.perform(contentRequest(post("/api/posts/$originId/quotes"), reader, "인용"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.quotedPostId").value(originId))
            .andExpect(jsonPath("$.parentPostId").doesNotExist())
            .andReturn().let { objectMapper.readTree(it.response.contentAsString)["id"].asLong() }

        mockMvc.perform(get("/api/posts/$originId").with(asUser(reader)))
            .andExpect(jsonPath("$.counts.replyCount").value(1))
            .andExpect(jsonPath("$.counts.quoteCount").value(1))
        assertCountsMatchRelations(originId)

        mockMvc.perform(put("/api/posts/$replyId/repost").with(asUser(author)).with(csrf()))
            .andExpect(status().isNoContent)
        val replyRepostRowId = postRepository.findAll().single { it.repostOfId == replyId }.id!!
        mockMvc.perform(get("/api/posts/$replyId").with(asUser(reader)))
            .andExpect(jsonPath("$.counts.repostCount").value(1))
        assertCountsMatchRelations(originId, replyId)

        repeat(2) {
            mockMvc.perform(delete("/api/posts/$replyId").with(asUser(reader)).with(csrf()))
                .andExpect(status().isNoContent)
        }
        mockMvc.perform(get("/api/posts/$originId").with(asUser(reader)))
            .andExpect(jsonPath("$.counts.replyCount").value(0))
            .andExpect(jsonPath("$.counts.quoteCount").value(1))
        mockMvc.perform(get("/api/posts/$replyRepostRowId").with(asUser(reader))).andExpect(status().isNotFound)
        assertEquals(0L, postCountsRepository.findById(replyId).get().repostCount)
        assertCountsMatchRelations(originId, replyId)
        mockMvc.perform(get("/api/posts/$replyId").with(asUser(reader)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))

        repeat(2) {
            mockMvc.perform(delete("/api/posts/$quoteId").with(asUser(reader)).with(csrf()))
                .andExpect(status().isNoContent)
        }
        mockMvc.perform(get("/api/posts/$originId").with(asUser(reader)))
            .andExpect(jsonPath("$.counts.replyCount").value(0))
            .andExpect(jsonPath("$.counts.quoteCount").value(0))
        assertCountsMatchRelations(originId)
    }

    @Test
    fun `작성자가 아니면 삭제할 수 없고 삭제된 게시글엔 아무것도 할 수 없다`() {
        val postId = createPost(author, "원본")

        mockMvc.perform(delete("/api/posts/$postId").with(asUser(reader)).with(csrf()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("NOT_POST_AUTHOR"))

        mockMvc.perform(delete("/api/posts/$postId").with(asUser(author)).with(csrf())).andExpect(status().isNoContent)
        assertNotNull(postRepository.findById(postId).get().deletedAt)

        listOf(postId, Long.MAX_VALUE).forEach { id ->
            mockMvc.perform(get("/api/posts/$id").with(asUser(reader))).andExpect(status().isNotFound)
            mockMvc.perform(contentRequest(post("/api/posts/$id/replies"), reader, "답글")).andExpect(status().isNotFound)
            mockMvc.perform(contentRequest(post("/api/posts/$id/quotes"), reader, "인용")).andExpect(status().isNotFound)
            mockMvc.perform(put("/api/posts/$id/repost").with(asUser(reader)).with(csrf()))
                .andExpect(status().isNotFound)
            mockMvc.perform(delete("/api/posts/$id/repost").with(asUser(reader)).with(csrf()))
                .andExpect(status().isNotFound)
            mockMvc.perform(put("/api/posts/$id/like").with(asUser(reader)).with(csrf())).andExpect(status().isNotFound)
            mockMvc.perform(delete("/api/posts/$id/like").with(asUser(reader)).with(csrf()))
                .andExpect(status().isNotFound)
            mockMvc.perform(post("/api/posts/$id/views").with(asUser(reader)).with(csrf()))
                .andExpect(status().isNotFound)
        }
        mockMvc.perform(delete("/api/posts/${Long.MAX_VALUE}").with(asUser(author)).with(csrf()))
            .andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/posts/$postId").with(asUser(reader)).with(csrf())).andExpect(status().isForbidden)
    }

    @Test
    fun `리포스트와 좋아요는 멱등하며 취소도 멱등하다`() {
        val postId = createPost(author, "원본")

        repeat(2) {
            mockMvc.perform(put("/api/posts/$postId/repost").with(asUser(reader)).with(csrf()))
                .andExpect(status().isNoContent)
        }
        repeat(2) {
            mockMvc.perform(put("/api/posts/$postId/like").with(asUser(reader)).with(csrf()))
                .andExpect(status().isNoContent)
        }
        mockMvc.perform(put("/api/posts/$postId/like").with(asUser(author)).with(csrf()))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/posts/$postId").with(asUser(reader)))
            .andExpect(jsonPath("$.counts.repostCount").value(1))
            .andExpect(jsonPath("$.counts.likeCount").value(2))
        assertEquals(2L, postRepository.count(), "리포스트는 본문 없는 게시글 행이다")
        val repostRow = postRepository.findAll().single { it.repostOfId == postId }
        assertEquals("", repostRow.content)
        assertEquals(reader.id, repostRow.authorId)
        assertCountsMatchRelations(postId)

        repeat(2) {
            mockMvc.perform(delete("/api/posts/$postId/repost").with(asUser(reader)).with(csrf()))
                .andExpect(status().isNoContent)
        }
        repeat(2) {
            mockMvc.perform(delete("/api/posts/$postId/like").with(asUser(reader)).with(csrf()))
                .andExpect(status().isNoContent)
        }

        mockMvc.perform(get("/api/posts/$postId").with(asUser(reader)))
            .andExpect(jsonPath("$.counts.repostCount").value(0))
            .andExpect(jsonPath("$.counts.likeCount").value(1))
        assertEquals(2L, postLikeRepository.count(), "좋아요 이력 행은 남는다")
        assertCountsMatchRelations(postId)

        mockMvc.perform(put("/api/posts/$postId/repost").with(asUser(reader)).with(csrf()))
            .andExpect(status().isNoContent)
        val reposts = postRepository.findAll().filter { it.repostOfId == postId }.sortedBy { it.id }
        assertEquals(2, reposts.size)
        assertNotNull(reposts[0].deletedAt)
        assertNull(reposts[1].deletedAt)
        assertCountsMatchRelations(postId)
    }

    @Test
    fun `리포스트 행에 대한 반응과 삭제는 원본에 귀속된다`() {
        val originalId = createPost(author, "원본")
        mockMvc.perform(put("/api/posts/$originalId/repost").with(asUser(reader)).with(csrf()))
            .andExpect(status().isNoContent)
        val repostRowId = postRepository.findAll().single { it.repostOfId == originalId }.id!!

        mockMvc.perform(get("/api/posts/$repostRowId").with(asUser(reader)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").value(""))
            .andExpect(jsonPath("$.repostOfId").value(originalId))
            .andExpect(jsonPath("$.counts.repostCount").value(0))

        mockMvc.perform(put("/api/posts/$repostRowId/like").with(asUser(author)).with(csrf()))
            .andExpect(status().isNoContent)
        mockMvc.perform(put("/api/posts/$repostRowId/repost").with(asUser(author)).with(csrf()))
            .andExpect(status().isNoContent)
        mockMvc.perform(post("/api/posts/$repostRowId/views").with(asUser(author)).with(csrf()))
            .andExpect(status().isNoContent)
        mockMvc.perform(contentRequest(post("/api/posts/$repostRowId/replies"), author, "답글"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.parentPostId").value(originalId))
        mockMvc.perform(contentRequest(post("/api/posts/$repostRowId/quotes"), author, "인용"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.quotedPostId").value(originalId))

        mockMvc.perform(get("/api/posts/$originalId").with(asUser(reader)))
            .andExpect(jsonPath("$.counts.likeCount").value(1))
            .andExpect(jsonPath("$.counts.repostCount").value(2))
            .andExpect(jsonPath("$.counts.viewCount").value(1))
            .andExpect(jsonPath("$.counts.replyCount").value(1))
            .andExpect(jsonPath("$.counts.quoteCount").value(1))
        mockMvc.perform(get("/api/posts/$repostRowId").with(asUser(reader)))
            .andExpect(jsonPath("$.counts.likeCount").value(0))
        assertCountsMatchRelations(originalId, repostRowId)

        mockMvc.perform(delete("/api/posts/$repostRowId/repost").with(asUser(author)).with(csrf()))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/posts/$originalId").with(asUser(reader)))
            .andExpect(jsonPath("$.counts.repostCount").value(1))
        assertNotNull(
            postRepository.findAll().single { it.repostOfId == originalId && it.authorId == author.id }.deletedAt
        )
        assertNull(postRepository.findById(repostRowId).get().deletedAt, "reader 의 리포스트 행은 그대로다")
        assertCountsMatchRelations(originalId)

        mockMvc.perform(delete("/api/posts/$repostRowId").with(asUser(reader)).with(csrf()))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/posts/$originalId").with(asUser(reader)))
            .andExpect(jsonPath("$.counts.repostCount").value(0))
        mockMvc.perform(delete("/api/posts/$originalId/repost").with(asUser(reader)).with(csrf()))
            .andExpect(status().isNoContent)
        assertCountsMatchRelations(originalId)
    }

    @Test
    fun `원본을 삭제하면 활성 리포스트 행도 함께 삭제된다`() {
        val originalId = createPost(author, "원본")
        val likers = List(3) { userRepository.saveAndFlush(user("reposter$it@example.com", "리포스터$it")) }
        likers.forEach {
            mockMvc.perform(put("/api/posts/$originalId/repost").with(asUser(it)).with(csrf()))
                .andExpect(status().isNoContent)
        }
        mockMvc.perform(delete("/api/posts/$originalId/repost").with(asUser(likers[0])).with(csrf()))
            .andExpect(status().isNoContent)
        val activeRepostIds =
            postRepository.findAll().filter { it.repostOfId == originalId && it.deletedAt == null }.map { it.id!! }
        assertEquals(2, activeRepostIds.size)

        mockMvc.perform(delete("/api/posts/$originalId").with(asUser(author)).with(csrf()))
            .andExpect(status().isNoContent)

        activeRepostIds.forEach { id ->
            mockMvc.perform(get("/api/posts/$id").with(asUser(reader))).andExpect(status().isNotFound)
            assertNotNull(postRepository.findById(id).get().deletedAt)
        }
        assertEquals(0L, postRepository.countActiveReposts(originalId))
        assertEquals(0L, postCountsRepository.findById(originalId).get().repostCount)
        mockMvc.perform(delete("/api/posts/$originalId").with(asUser(author)).with(csrf()))
            .andExpect(status().isNoContent)
        assertEquals(0L, postCountsRepository.findById(originalId).get().repostCount)
    }

    @Test
    fun `DB 제약이 리포스트 무본문과 참조 배타를 최종 방어한다`() {
        val originalId = createPost(author, "원본")
        listOf(
            Post(authorId = author.id!!, content = "", repostOfId = null),
            Post(authorId = author.id!!, content = "본문", repostOfId = originalId),
            Post(authorId = author.id!!, content = "본문", parentPostId = originalId, quotedPostId = originalId),
        ).forEach { invalid ->
            assertFailsWith<DataIntegrityViolationException> { postRepository.saveAndFlush(invalid) }
        }
        assertEquals(1L, postRepository.count())
    }

    @Test
    fun `조회수는 사용자당 한 번만 센다`() {
        val postId = createPost(author, "원본")

        repeat(3) {
            mockMvc.perform(post("/api/posts/$postId/views").with(asUser(reader)).with(csrf()))
                .andExpect(status().isNoContent)
        }
        mockMvc.perform(post("/api/posts/$postId/views").with(asUser(author)).with(csrf()))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/posts/$postId").with(asUser(reader)))
            .andExpect(jsonPath("$.counts.viewCount").value(2))
        assertCountsMatchRelations(postId)
    }

    @Test
    fun `동시에 좋아요해도 관계는 하나만 생기고 통계도 하나만 오른다`() {
        val postId = createPost(author, "원본")
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)

        try {
            val attempts = List(CONCURRENT_REQUESTS) {
                executor.submit<Int> {
                    start.await()
                    mockMvc.perform(put("/api/posts/$postId/like").with(asUser(reader)).with(csrf()))
                        .andReturn().response.status
                }
            }
            start.countDown()
            val statuses = attempts.map { it.get(10, TimeUnit.SECONDS) }

            assertEquals(List(CONCURRENT_REQUESTS) { 204 }, statuses)
            assertEquals(1L, postLikeRepository.count())
            assertEquals(1L, postCountsRepository.findById(postId).get().likeCount)
            assertCountsMatchRelations(postId)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `서로 다른 사용자들이 동시에 좋아요하면 통계는 정확히 사용자 수만큼 오른다`() {
        val postId = createPost(author, "원본")
        val users = List(CONCURRENT_REQUESTS) { userRepository.saveAndFlush(user("liker$it@example.com", "독자$it")) }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)

        try {
            val attempts = users.map { liker ->
                executor.submit<Int> {
                    start.await()
                    mockMvc.perform(put("/api/posts/$postId/like").with(asUser(liker)).with(csrf()))
                        .andReturn().response.status
                }
            }
            start.countDown()
            val statuses = attempts.map { it.get(20, TimeUnit.SECONDS) }

            assertEquals(List(CONCURRENT_REQUESTS) { 204 }, statuses)
            assertEquals(CONCURRENT_REQUESTS.toLong(), postLikeRepository.count())
            assertEquals(CONCURRENT_REQUESTS.toLong(), postCountsRepository.findById(postId).get().likeCount)
            assertCountsMatchRelations(postId)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `인증 없이 게시글을 작성하거나 조회할 수 없고 CSRF 없이 상태를 바꿀 수 없다`() {
        val postId = createPost(author, "원본")

        mockMvc.perform(
            post("/api/posts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""{"content":"x"}"""),
        ).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/posts/$postId")).andExpect(status().isUnauthorized)

        mockMvc.perform(put("/api/posts/$postId/like").with(asUser(reader))).andExpect(status().isForbidden)
        mockMvc.perform(delete("/api/posts/$postId").with(asUser(author))).andExpect(status().isForbidden)
        mockMvc.perform(post("/api/posts/$postId/views").with(asUser(reader))).andExpect(status().isForbidden)
        mockMvc.perform(
            post("/api/posts").with(asUser(author)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"content":"x"}"""),
        ).andExpect(status().isForbidden)
        assertEquals(0L, postLikeRepository.count())
        assertEquals(0L, postViewRepository.count())
        assertEquals(1L, postRepository.count())
    }

    private fun createPost(user: User, content: String): Long =
        mockMvc.perform(contentRequest(post("/api/posts"), user, content))
            .andExpect(status().isCreated)
            .andReturn().let { objectMapper.readTree(it.response.contentAsString)["id"].asLong() }

    private fun contentRequest(
        builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder,
        user: User,
        content: String,
    ) = builder.with(asUser(user)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(mapOf("content" to content)))

    private fun asUser(user: User) = user(user.id.toString())

    private fun assertCountsMatchRelations(vararg postIds: Long) {
        postIds.forEach { postId ->
            val counts = postCountsRepository.findById(postId).orElse(null)
            assertEquals(postRepository.countActiveReplies(postId), counts?.replyCount ?: 0L, "replyCount of $postId")
            assertEquals(postRepository.countActiveQuotes(postId), counts?.quoteCount ?: 0L, "quoteCount of $postId")
            assertEquals(postRepository.countActiveReposts(postId), counts?.repostCount ?: 0L, "repostCount of $postId")
            assertEquals(
                postLikeRepository.countActiveByPostId(postId),
                counts?.likeCount ?: 0L,
                "likeCount of $postId"
            )
            assertEquals(postViewRepository.countByPostId(postId), counts?.viewCount ?: 0L, "viewCount of $postId")
        }
    }

    private fun user(email: String, nickname: String) =
        User(email = email, passwordHash = "encoded", nickname = nickname)

    private companion object {
        const val CONCURRENT_REQUESTS = 8
    }
}

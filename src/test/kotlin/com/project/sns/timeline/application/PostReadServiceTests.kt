package com.project.sns.timeline.application

import com.project.sns.media.application.MediaDetail
import com.project.sns.media.application.MediaRef
import com.project.sns.media.application.MediaViewService
import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostCountsRepository
import com.project.sns.post.domain.PostRepository
import com.project.sns.timeline.domain.PostCache
import com.project.sns.timeline.domain.PostSnapshot
import java.net.URL
import java.time.Instant
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.test.assertEquals

class PostReadServiceTests {
    private val cache = mock(PostCache::class.java)
    private val postRepository = mock(PostRepository::class.java)
    private val countsRepository = mock(PostCountsRepository::class.java)
    private val mediaViewService = mock(MediaViewService::class.java)
    private val service = PostReadService(cache, postRepository, countsRepository, mediaViewService)

    init {
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as Collection<Long>).filter { it != DELETED }
                .map { Post(authorId = 9L, content = "본문 $it", id = it) }
        }.`when`(postRepository).findActiveByIds(anyCollection())
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as Collection<Long>).filter { it != DELETED }.toSet()
        }.`when`(postRepository).findActiveIds(anyCollection())
        doReturn(emptyMap<Long, Any>()).`when`(mediaViewService).listRefsForPosts(anyCollection())
    }

    @Test
    fun `캐시 적중분은 DB 를 안 읽고 미스만 읽어 캐시에 넣으며 통계는 항상 DB 에서 온다`() {
        doReturn(mapOf(1L to snapshot(1L))).`when`(cache).getAll(setOf(1L, 2L))
        doReturn(mapOf(2L to PostCounts(postId = 2L, likeCount = 7))).`when`(countsRepository).getAll(anyCollection())

        val result = service.loadActive(listOf(1L, 2L))

        assertEquals(setOf(1L, 2L), result.keys)
        assertEquals(7L, result.getValue(2L).counts.likeCount)
        verify(postRepository).findActiveByIds(listOf(2L))
        verify(cache).putAll(org.mockito.ArgumentMatchers.argThat<Collection<PostSnapshot>> {
            it != null && it.map { s -> s.id } == listOf(
                2L
            )
        } ?: emptyList())
    }

    @Test
    fun `삭제된 글은 캐시에 들어가지 않고 결과에서도 빠진다`() {
        doReturn(emptyMap<Long, PostSnapshot>()).`when`(cache).getAll(setOf(3L))
        doReturn(emptyMap<Long, Any>()).`when`(countsRepository).getAll(anyCollection())

        assertEquals(setOf(3L), service.loadActive(listOf(DELETED, 3L)).keys)
        verify(cache).putAll(org.mockito.ArgumentMatchers.argThat<Collection<PostSnapshot>> {
            it != null && it.map { s -> s.id } == listOf(
                3L
            )
        } ?: emptyList())
    }

    @Test
    fun `캐시에 스냅샷이 남아 있어도 삭제된 글은 빠진다`() {
        doReturn(mapOf(DELETED to snapshot(DELETED), 3L to snapshot(3L))).`when`(cache).getAll(anyCollection())
        doReturn(emptyMap<Long, Any>()).`when`(countsRepository).getAll(anyCollection())

        assertEquals(setOf(3L), service.loadActive(listOf(DELETED, 3L)).keys)
        verify(cache).getAll(setOf(3L))
        verify(postRepository, never()).findActiveByIds(anyCollection())
    }

    @Test
    fun `캐시가 죽어도 DB 로 진행한다`() {
        doThrow(RuntimeException("redis down")).`when`(cache).getAll(anyCollection())
        doThrow(RuntimeException("redis down")).`when`(cache).putAll(anyCollection())
        doReturn(emptyMap<Long, Any>()).`when`(countsRepository).getAll(anyCollection())

        assertEquals(setOf(3L), service.loadActive(listOf(3L)).keys)
    }

    @Test
    fun `미디어는 스토리지 키로 스냅샷에 들어가고 URL 은 읽을 때 서명한다`() {
        val ref = MediaRef(id = 5L, contentType = "image/png", sizeBytes = 10, width = 1, height = 1, storageKey = "media/1/5.png")
        doReturn(emptyMap<Long, PostSnapshot>()).`when`(cache).getAll(setOf(3L))
        doReturn(mapOf(3L to listOf(ref))).`when`(mediaViewService).listRefsForPosts(anyCollection())
        doReturn(MediaDetail(id = 5L, contentType = "image/png", sizeBytes = 10, width = 1, height = 1, url = URL("https://signed/5"))).`when`(mediaViewService).sign(ref)
        doReturn(emptyMap<Long, Any>()).`when`(countsRepository).getAll(anyCollection())

        assertEquals("https://signed/5", service.loadActive(listOf(3L)).getValue(3L).media.single().url.toString())
        verify(cache).putAll(org.mockito.ArgumentMatchers.argThat<Collection<PostSnapshot>> { it != null && it.single().media.single().storageKey == "media/1/5.png" } ?: emptyList())
    }

    private fun snapshot(id: Long) = PostSnapshot(
        id = id,
        authorId = 9L,
        content = "캐시 $id",
        parentPostId = null,
        quotedPostId = null,
        repostOfId = null,
        createdAt = Instant.now(),
        media = emptyList()
    )

    private companion object {
        const val DELETED = 99L
    }
}

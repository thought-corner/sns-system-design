package com.project.sns.timeline.application.filter

import com.project.sns.post.application.PostDetail
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostViewRepository
import com.project.sns.timeline.domain.TimelinePolicy
import java.time.Instant
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.assertEquals

class TimelineFilterTests {
    private val postViewRepository = mock(PostViewRepository::class.java)

    @Test
    fun `이미 읽은 글은 빠진다`() {
        val posts = listOf(post(30L), post(29L), post(28L))
        doReturn(setOf(29L)).`when`(postViewRepository).findViewedPostIds(ME, listOf(30L, 29L, 28L))

        assertEquals(listOf(30L, 28L), AlreadyReadFilter(postViewRepository).filter(ME, posts).map { it.id })
    }

    @Test
    fun `리포스트 행은 원본을 읽었으면 빠진다`() {
        val posts = listOf(post(31L, repostOfId = 10L), post(30L), post(10L))
        doReturn(setOf(10L)).`when`(postViewRepository).findViewedPostIds(ME, listOf(10L, 30L))

        assertEquals(listOf(30L), AlreadyReadFilter(postViewRepository).filter(ME, posts).map { it.id })
    }

    @Test
    fun `빈 목록은 조회 없이 그대로 돌려준다`() {
        AlreadyReadFilter(postViewRepository).filter(ME, emptyList())
        verifyNoInteractions(postViewRepository)
    }

    @Test
    fun `설정한 기간보다 오래된 글은 빠진다`() {
        val filter = StaleContentFilter()
        val fresh = post(30L, createdAt = Instant.now().minus(TimelinePolicy.STALE_MAX_AGE).plusSeconds(60))
        val stale = post(29L, createdAt = Instant.now().minus(TimelinePolicy.STALE_MAX_AGE).minusSeconds(60))

        assertEquals(listOf(30L), filter.filter(ME, listOf(fresh, stale)).map { it.id })
    }

    private fun post(id: Long, createdAt: Instant = Instant.now(), repostOfId: Long? = null) = PostDetail(
        id = id, authorId = 9L, content = "본문", parentPostId = null, quotedPostId = null, repostOfId = repostOfId,
        createdAt = createdAt, counts = PostCounts(postId = id), media = emptyList(),
    )

    private companion object {
        const val ME = 1L
    }
}

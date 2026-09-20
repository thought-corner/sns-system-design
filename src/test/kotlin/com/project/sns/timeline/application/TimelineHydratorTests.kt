package com.project.sns.timeline.application

import com.project.sns.post.application.PostDetail
import com.project.sns.post.domain.PostCounts
import java.time.Instant
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimelineHydratorTests {
    private val postReadService = mock(PostReadService::class.java)
    private val hydrator = TimelineHydrator(postReadService)

    private val db = mapOf(
        40L to detail(40L, repostOfId = 5L),
        30L to detail(30L, repostOfId = 6L),
        20L to detail(20L),
        5L to detail(5L),
    )

    init {
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as Collection<Long>).mapNotNull { id -> db[id]?.let { id to it } }.toMap()
        }.`when`(postReadService).loadActive(anyCollection())
    }

    @Test
    fun `활성 글만 후보 순서대로 남긴다`() {
        assertEquals(listOf(40L, 20L), hydrator.loadActive(listOf(40L, 99L, 20L)).map { it.id })
    }

    @Test
    fun `리포스트 행은 원본을 함께 싣고 원본이 없으면 항목에서 뺀다`() {
        val items = hydrator.toItems(listOf(db.getValue(40L), db.getValue(30L), db.getValue(20L)))

        assertEquals(listOf(40L, 20L), items.map { it.post.id })
        assertEquals(5L, items[0].original?.id)
        assertNull(items[1].original)
        verify(postReadService).loadActive(listOf(5L, 6L))
    }

    @Test
    fun `리포스트가 없으면 원본 조회를 하지 않는다`() {
        hydrator.toItems(listOf(db.getValue(20L)))
        verify(postReadService, never()).loadActive(emptyList())
    }

    private fun detail(id: Long, repostOfId: Long? = null) = PostDetail(
        id = id,
        authorId = 9L,
        content = if (repostOfId == null) "본문" else "",
        parentPostId = null,
        quotedPostId = null,
        repostOfId = repostOfId,
        createdAt = Instant.now(),
        counts = PostCounts(postId = id),
        media = emptyList(),
    )
}

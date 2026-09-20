package com.project.sns.timeline.application

import com.project.sns.post.application.PostDetail
import com.project.sns.post.domain.PostCounts
import com.project.sns.timeline.domain.TimelineCandidateFilter
import com.project.sns.timeline.domain.TimelineCandidateSource
import com.project.sns.timeline.domain.TimelineEntry
import java.time.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimelineServiceTests {
    private val hydrator = FakeHydrator()

    @Test
    fun `소스 후보를 합쳐 id 내림차순으로 정렬하고 다음 커서는 마지막 항목 id 다`() {
        val service = service(sources = listOf(source(30L, 28L), source(29L, 27L)))

        val page = service.read(ME, cursor = null, limit = 2)

        assertEquals(listOf(30L, 29L), page.items.map { it.post.id })
        assertEquals(29L, page.nextCursor)
    }

    @Test
    fun `소스에는 limit 의 1점5배 더하기 1 만큼 요청한다`() {
        var requested = -1
        val probe = TimelineCandidateSource { _, _, limit -> requested = limit; emptyList() }
        service(sources = listOf(probe)).read(ME, cursor = null, limit = 10)
        assertEquals(16, requested)
    }

    @Test
    fun `필터는 순서대로 적용되고 걸러진 항목은 응답에서 빠진다`() {
        val dropOdd = TimelineCandidateFilter { _, posts -> posts.filter { it.id % 2 == 0L } }
        val dropAbove28 = TimelineCandidateFilter { _, posts -> posts.filter { it.id <= 28L } }
        val service = service(sources = listOf(source(30L, 29L, 28L, 27L, 26L)), filters = listOf(dropOdd, dropAbove28))

        val page = service.read(ME, cursor = null, limit = 5)

        assertEquals(listOf(28L, 26L), page.items.map { it.post.id })
        assertNull(page.nextCursor)
    }

    @Test
    fun `후보가 fetch 보다 적고 전부 소비했으면 마지막 페이지다`() {
        hydrator.deleted = setOf(29L)
        val service = service(sources = listOf(source(30L, 29L)))

        val page = service.read(ME, cursor = null, limit = 2)

        assertEquals(listOf(30L), page.items.map { it.post.id })
        assertNull(page.nextCursor)
    }

    @Test
    fun `후보가 fetch 를 채웠는데 전부 걸러지면 커서는 마지막 후보 id 라 이어 읽을 수 있다`() {
        val dropAll = TimelineCandidateFilter { _, _ -> emptyList() }
        val service = service(sources = listOf(source(30L, 29L, 28L, 27L)), filters = listOf(dropAll))

        val page = service.read(ME, cursor = null, limit = 2)

        assertEquals(emptyList<Long>(), page.items.map { it.post.id })
        assertEquals(27L, page.nextCursor)
    }

    @Test
    fun `limit 는 상한으로 잘리고 커서는 소스에 그대로 전달된다`() {
        var seen: Pair<Long?, Int>? = null
        val probe = TimelineCandidateSource { _, before, limit -> seen = before to limit; emptyList() }
        service(sources = listOf(probe)).read(ME, cursor = 100L, limit = 500)
        assertEquals(100L to 76, seen)
    }

    private fun service(sources: List<TimelineCandidateSource>, filters: List<TimelineCandidateFilter> = emptyList()) =
        TimelineService(sources, filters, hydrator)

    private fun source(vararg ids: Long) = TimelineCandidateSource { _, before, limit ->
        ids.filter { before == null || it < before }.take(limit).map { TimelineEntry(it, it) }
    }

    private class FakeHydrator : TimelineHydrator(org.mockito.Mockito.mock(PostReadService::class.java)) {
        var deleted: Set<Long> = emptySet()

        override fun loadActive(ids: List<Long>): List<PostDetail> = ids.filterNot { it in deleted }.map { detail(it) }

        override fun toItems(posts: List<PostDetail>): List<TimelineItem> =
            posts.map { TimelineItem(post = it, original = null) }

        private fun detail(id: Long) = PostDetail(
            id = id, authorId = 9L, content = "본문 $id", parentPostId = null, quotedPostId = null, repostOfId = null,
            createdAt = Instant.now(), counts = PostCounts(postId = id), media = emptyList(),
        )
    }

    private companion object {
        const val ME = 1L
    }
}

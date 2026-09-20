package com.project.sns.timeline.application.source

import com.project.sns.timeline.application.TimelineFanoutService
import com.project.sns.timeline.domain.PostCandidateRepository
import com.project.sns.timeline.domain.TimelineEntry
import com.project.sns.timeline.domain.TimelinePolicy
import java.time.Instant
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import kotlin.test.assertEquals

class PopularCandidateSourceTests {
    private val candidates = mock(PostCandidateRepository::class.java)
    private val fanoutService = mock(TimelineFanoutService::class.java)
    private val source = PopularCandidateSource(candidates, fanoutService)

    @Test
    fun `정책의 좋아요 하한과 기간으로 인기 글을 조회해 점수를 붙인다`() {
        doReturn(listOf(30L, 20L)).`when`(candidates)
            .findPopularPostIds(any(Instant::class.java) ?: Instant.EPOCH, eq(TimelinePolicy.POPULAR_MIN_LIKES), eq(50L), eq(5))
        doReturn(listOf(TimelineEntry(30L, 30L), TimelineEntry(20L, 20L))).`when`(fanoutService).entriesOf(listOf(30L, 20L))

        assertEquals(listOf(TimelineEntry(30L, 30L), TimelineEntry(20L, 20L)), source.collect(1L, 50L, 5))
    }

    @Test
    fun `후보가 없으면 빈 목록이다`() {
        doReturn(emptyList<Long>()).`when`(candidates).findPopularPostIds(any(Instant::class.java) ?: Instant.EPOCH, anyLong(), any(), anyInt())
        doReturn(emptyList<TimelineEntry>()).`when`(fanoutService).entriesOf(emptyList())

        assertEquals(emptyList(), source.collect(1L, null, 5))
    }
}

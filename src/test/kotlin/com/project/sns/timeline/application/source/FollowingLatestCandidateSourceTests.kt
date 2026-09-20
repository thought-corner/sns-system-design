package com.project.sns.timeline.application.source

import com.project.sns.timeline.application.FollowGraphService
import com.project.sns.timeline.application.TimelineFanoutService
import com.project.sns.timeline.domain.PostCandidateRepository
import com.project.sns.timeline.domain.TimelineEntry
import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.timeline.domain.TimelineSlice
import com.project.sns.timeline.domain.TimelineStore
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.test.assertEquals

class FollowingLatestCandidateSourceTests {
    private val store = mock(TimelineStore::class.java)
    private val followGraph = mock(FollowGraphService::class.java)
    private val candidates = mock(PostCandidateRepository::class.java)
    private val fanoutService = mock(TimelineFanoutService::class.java)
    private val source = FollowingLatestCandidateSource(store, followGraph, candidates, fanoutService)

    init {
        doReturn(emptySet<Long>()).`when`(followGraph).followedCelebrityIds(anyLong())
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as List<Long>).map { TimelineEntry(it, it) }
        }.`when`(fanoutService).entriesOf(anyList())
    }

    @Test
    fun `홈 타임라인이 있으면 그대로 읽는다`() {
        doReturn(TimelineSlice(true, entries(30L, 29L, 28L))).`when`(store).readHome(ME, null, 4)

        assertEquals(entries(30L, 29L, 28L), source.collect(ME, null, 4))
        verify(candidates, never()).findRecentPostIdsByAuthors(anyCollection(), anyInt())
    }

    @Test
    fun `키가 없으면 팔로우 캐시의 작성자 집합으로 재구축해 저장하고 커서 뒤부터 돌려준다`() {
        doReturn(TimelineSlice(false, emptyList())).`when`(store).readHome(ME, 29L, 4)
        doReturn(setOf(2L, 3L, CELEB)).`when`(followGraph).followingIds(ME)
        doReturn(setOf(CELEB)).`when`(followGraph).followedCelebrityIds(ME)
        doReturn(TimelineSlice(true, emptyList())).`when`(store).readAuthor(CELEB, 29L, 4)
        doReturn(listOf(30L, 29L, 28L, 27L)).`when`(candidates).findRecentPostIdsByAuthors(setOf(2L, 3L, ME), TimelinePolicy.HOME_TIMELINE_SIZE)

        assertEquals(entries(28L, 27L), source.collect(ME, 29L, 4))
        val order = inOrder(store, candidates)
        order.verify(store).beginRebuildHome(ME)
        order.verify(candidates).findRecentPostIdsByAuthors(setOf(2L, 3L, ME), TimelinePolicy.HOME_TIMELINE_SIZE)
        order.verify(store).rebuildHome(ME, entries(30L, 29L, 28L, 27L))
    }

    @Test
    fun `대형 계정의 작성자 타임라인을 점수 순으로 병합하고 없으면 재구축한다`() {
        doReturn(TimelineSlice(true, entries(30L, 20L))).`when`(store).readHome(ME, null, 3)
        doReturn(setOf(CELEB, CELEB2)).`when`(followGraph).followedCelebrityIds(ME)
        doReturn(TimelineSlice(true, entries(25L, 15L))).`when`(store).readAuthor(CELEB, null, 3)
        doReturn(TimelineSlice(false, emptyList())).`when`(store).readAuthor(CELEB2, null, 3)
        doReturn(listOf(28L)).`when`(candidates).findRecentPostIdsByAuthor(CELEB2, TimelinePolicy.AUTHOR_TIMELINE_SIZE)

        assertEquals(entries(30L, 28L, 25L), source.collect(ME, null, 3))
        verify(store).rebuildAuthor(CELEB2, entries(28L))
    }

    @Test
    fun `타임라인 캐시가 죽어도 PostgreSQL 후보로 응답한다`() {
        doThrow(RuntimeException("redis down")).`when`(store).readHome(ME, null, 4)
        doThrow(RuntimeException("redis down")).`when`(store).rebuildHome(anyLong(), anyList())
        doReturn(setOf(2L)).`when`(followGraph).followingIds(ME)
        doReturn(listOf(30L, 29L)).`when`(candidates).findRecentPostIdsByAuthors(setOf(2L, ME), TimelinePolicy.HOME_TIMELINE_SIZE)

        assertEquals(entries(30L, 29L), source.collect(ME, null, 4))
    }

    private fun entries(vararg ids: Long) = ids.map { TimelineEntry(it, it) }

    private companion object {
        const val ME = 1L
        const val CELEB = 8L
        const val CELEB2 = 9L
    }
}

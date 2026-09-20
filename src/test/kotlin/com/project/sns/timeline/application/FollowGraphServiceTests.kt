package com.project.sns.timeline.application

import com.project.sns.timeline.domain.FollowCache
import com.project.sns.timeline.domain.SocialGraph
import com.project.sns.timeline.domain.TimelinePolicy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowGraphServiceTests {
    private val cache = mock(FollowCache::class.java)
    private val graph = mock(SocialGraph::class.java)
    private val service = FollowGraphService(cache, graph)

    @Test
    fun `팔로잉 집합은 캐시 적중이면 DB 를 안 보고 미스면 읽어서 채운다`() {
        doReturn(setOf(2L, 3L)).`when`(cache).followingIds(ME)
        assertEquals(setOf(2L, 3L), service.followingIds(ME))
        verify(graph, never()).followingIds(ME)

        doReturn(null).`when`(cache).followingIds(OTHER)
        doReturn(listOf(4L)).`when`(graph).followingIds(OTHER)
        assertEquals(setOf(4L), service.followingIds(OTHER))
        verify(cache).putFollowingIds(OTHER, listOf(4L))
    }

    @Test
    fun `대형 계정 목록도 같은 읽기 관통이고 임계값은 설정에서 온다`() {
        doReturn(null).`when`(cache).celebrityIds(ME)
        doReturn(listOf(9L)).`when`(graph).followedCelebrityIds(ME, TimelinePolicy.CELEBRITY_MAX_FOLLOWERS)

        assertEquals(setOf(9L), service.followedCelebrityIds(ME))
        verify(cache).putCelebrityIds(ME, listOf(9L))
    }

    @Test
    fun `대형 계정 판정은 팔로워 수 통계로`() {
        doReturn(TimelinePolicy.CELEBRITY_MAX_FOLLOWERS).`when`(graph).followerCount(9L)
        doReturn(TimelinePolicy.CELEBRITY_MAX_FOLLOWERS - 1).`when`(graph).followerCount(8L)
        assertTrue(service.isCelebrity(9L))
        assertFalse(service.isCelebrity(8L))
    }

    @Test
    fun `팔로우 변경은 팔로잉·대형 계정 캐시를 비운다`() {
        service.onFollowChanged(ME)
        verify(cache).evictFollowingIds(ME)
        verify(cache).evictCelebrityIds(ME)
    }

    @Test
    fun `캐시가 죽어도 PostgreSQL 로 진행한다`() {
        doThrow(RuntimeException("redis down")).`when`(cache).followingIds(ME)
        doThrow(RuntimeException("redis down")).`when`(cache).putFollowingIds(ME, listOf(2L))
        doReturn(listOf(2L)).`when`(graph).followingIds(ME)

        assertEquals(setOf(2L), service.followingIds(ME))
    }

    private companion object {
        const val ME = 1L
        const val OTHER = 7L
    }
}

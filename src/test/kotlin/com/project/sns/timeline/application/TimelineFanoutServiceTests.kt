package com.project.sns.timeline.application

import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostRepository
import com.project.sns.timeline.domain.FanoutEvent
import com.project.sns.timeline.domain.PostCandidateRepository
import com.project.sns.timeline.domain.RankingService
import com.project.sns.timeline.domain.SocialGraph
import com.project.sns.timeline.domain.TimelineEntry
import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.timeline.domain.TimelineStore
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import kotlin.test.assertEquals

class TimelineFanoutServiceTests {
    private val store = mock(TimelineStore::class.java)
    private val socialGraph = mock(SocialGraph::class.java)
    private val followGraphService = mock(FollowGraphService::class.java)
    private val candidates = mock(PostCandidateRepository::class.java)
    private val postRepository = mock(PostRepository::class.java)
    private val ranking = RankingService { postId, _ -> postId * 10 }
    private val service =
        TimelineFanoutService(store, socialGraph, followGraphService, candidates, postRepository, ranking)

    init {
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as Collection<Long>).map { Post(authorId = 9L, content = "본문", id = it) }
        }.`when`(postRepository).findActiveByIds(anyCollection())
    }

    @Test
    fun `일반 작성자의 글은 랭킹 점수로 본인과 팔로워 전원의 홈 타임라인에 페이지 크기 단위로 들어간다`() {
        doReturn(false).`when`(followGraphService).isCelebrity(AUTHOR)
        val firstPage = (1..TimelinePolicy.Fanout.FOLLOWER_PAGE_SIZE).map { 100L + it }
        doReturn(firstPage).`when`(socialGraph).followerIds(AUTHOR, 0L, TimelinePolicy.Fanout.FOLLOWER_PAGE_SIZE)
        doReturn(listOf(9_999L)).`when`(socialGraph)
            .followerIds(AUTHOR, firstPage.last(), TimelinePolicy.Fanout.FOLLOWER_PAGE_SIZE)

        service.fanout(FanoutEvent(postId = POST, authorId = AUTHOR))

        val entry = TimelineEntry(POST, POST * 10)
        val order = inOrder(store)
        order.verify(store).pushHome(listOf(AUTHOR), entry)
        order.verify(store).pushHome(firstPage, entry)
        order.verify(store).pushHome(listOf(9_999L), entry)
        verify(store, never()).pushAuthor(AUTHOR, entry)
    }

    @Test
    fun `대형 계정의 글은 본인 홈과 작성자 타임라인에만 들어가고 팔로워를 조회하지 않는다`() {
        doReturn(true).`when`(followGraphService).isCelebrity(AUTHOR)

        service.fanout(FanoutEvent(postId = POST, authorId = AUTHOR))

        verify(store).pushHome(listOf(AUTHOR), TimelineEntry(POST, POST * 10))
        verify(store).pushAuthor(AUTHOR, TimelineEntry(POST, POST * 10))
        verifyNoMoreInteractions(store)
        verify(socialGraph, never()).followerIds(AUTHOR, 0L, TimelinePolicy.Fanout.FOLLOWER_PAGE_SIZE)
    }

    @Test
    fun `팔로우하면 상대의 최근 글을 백필하고 대형 계정이면 건너뛴다`() {
        doReturn(false).`when`(followGraphService).isCelebrity(FOLLOWING)
        doReturn(listOf(30L, 29L, 28L)).`when`(candidates)
            .findRecentPostIdsByAuthor(FOLLOWING, TimelinePolicy.BACKFILL_SIZE)
        service.backfillAfterFollow(FOLLOWER, FOLLOWING)
        verify(store).pushHome(listOf(FOLLOWER), TimelineEntry(30L, 300L))
        verify(store).pushHome(listOf(FOLLOWER), TimelineEntry(28L, 280L))

        doReturn(true).`when`(followGraphService).isCelebrity(CELEB)
        service.backfillAfterFollow(FOLLOWER, CELEB)
        verify(candidates, never()).findRecentPostIdsByAuthor(CELEB, TimelinePolicy.BACKFILL_SIZE)
    }

    @Test
    fun `언팔로우하면 상대의 최근 글을 홈 상한만큼 지운다`() {
        doReturn(listOf(30L, 29L)).`when`(candidates).findRecentPostIdsByAuthor(FOLLOWING, TimelinePolicy.HOME_TIMELINE_SIZE)

        service.removeAfterUnfollow(FOLLOWER, FOLLOWING)

        verify(store).removeHome(FOLLOWER, listOf(30L, 29L))
    }

    @Test
    fun `entriesOf 는 랭킹 점수를 붙이고 활성 아닌 글은 EPOCH 기준으로 점수를 매긴다`() {
        assertEquals(listOf(TimelineEntry(5L, 50L), TimelineEntry(6L, 60L)), service.entriesOf(listOf(5L, 6L)))
    }

    private companion object {
        const val AUTHOR = 1L
        const val POST = 100L
        const val FOLLOWER = 2L
        const val FOLLOWING = 3L
        const val CELEB = 4L
    }
}

package com.project.sns.timeline.application

import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.post.domain.PostRepository
import com.project.sns.timeline.domain.FanoutEvent
import com.project.sns.timeline.domain.PostCandidateRepository
import com.project.sns.timeline.domain.RankingService
import com.project.sns.timeline.domain.SocialGraph
import com.project.sns.timeline.domain.TimelineEntry
import com.project.sns.timeline.domain.TimelineStore
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class TimelineFanoutService(
    private val timelineStore: TimelineStore,
    private val socialGraph: SocialGraph,
    private val followGraphService: FollowGraphService,
    private val postCandidateRepository: PostCandidateRepository,
    private val postRepository: PostRepository,
    private val rankingService: RankingService,
) {
    fun fanout(event: FanoutEvent) {
        val entry = entryOf(event.postId)
        timelineStore.pushHome(listOf(event.authorId), entry)
        if (followGraphService.isCelebrity(event.authorId)) {
            timelineStore.pushAuthor(event.authorId, entry)
            return
        }
        var after = 0L
        while (true) {
            val page = socialGraph.followerIds(event.authorId, after, TimelinePolicy.Fanout.FOLLOWER_PAGE_SIZE)
            if (page.isEmpty()) break
            timelineStore.pushHome(page, entry)
            if (page.size < TimelinePolicy.Fanout.FOLLOWER_PAGE_SIZE) break
            after = page.last()
        }
    }

    fun backfillAfterFollow(followerId: Long, followingId: Long) {
        if (followGraphService.isCelebrity(followingId)) return
        entriesOf(postCandidateRepository.findRecentPostIdsByAuthor(followingId, TimelinePolicy.BACKFILL_SIZE))
            .forEach { timelineStore.pushHome(listOf(followerId), it) }
    }

    fun removeAfterUnfollow(followerId: Long, followingId: Long) {
        timelineStore.removeHome(
            followerId,
            postCandidateRepository.findRecentPostIdsByAuthor(followingId, TimelinePolicy.HOME_TIMELINE_SIZE)
        )
    }

    fun entriesOf(postIds: List<Long>): List<TimelineEntry> {
        if (postIds.isEmpty()) return emptyList()
        val createdAt = postRepository.findActiveByIds(postIds).associate { requireNotNull(it.id) to it.createdAt }
        return postIds.map {
            TimelineEntry(
                postId = it,
                score = rankingService.score(it, createdAt[it] ?: Instant.EPOCH)
            )
        }
    }

    private fun entryOf(postId: Long): TimelineEntry = entriesOf(listOf(postId)).single()
}

package com.project.sns.timeline.application.source

import com.project.sns.timeline.application.FollowGraphService
import com.project.sns.timeline.application.TimelineFanoutService
import com.project.sns.timeline.domain.PostCandidateRepository
import com.project.sns.timeline.domain.TimelineCandidateSource
import com.project.sns.timeline.domain.TimelineEntry
import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.timeline.domain.TimelineSlice
import com.project.sns.timeline.domain.TimelineStore
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(0)
class FollowingLatestCandidateSource(
    private val timelineStore: TimelineStore,
    private val followGraphService: FollowGraphService,
    private val postCandidateRepository: PostCandidateRepository,
    private val fanoutService: TimelineFanoutService,
) : TimelineCandidateSource {
    override fun collect(userId: Long, beforeScore: Long?, limit: Int): List<TimelineEntry> {
        val home = readHome(userId, beforeScore, limit)
        val celebrities = followGraphService.followedCelebrityIds(userId).flatMap { readAuthor(it, beforeScore, limit) }
        return (home + celebrities).distinctBy { it.postId }.sortedByDescending { it.score }.take(limit)
    }

    private fun readHome(userId: Long, beforeScore: Long?, limit: Int): List<TimelineEntry> {
        val slice = safely("홈 읽기") { timelineStore.readHome(userId, beforeScore, limit) } ?: MISSING
        if (slice.exists) return slice.entries
        safely("홈 재구축 시작") { timelineStore.beginRebuildHome(userId) }
        val authors = followGraphService.followingIds(userId) - followGraphService.followedCelebrityIds(userId) + userId
        val rebuilt = fanoutService.entriesOf(postCandidateRepository.findRecentPostIdsByAuthors(authors, TimelinePolicy.HOME_TIMELINE_SIZE))
        safely("홈 재구축 저장") { timelineStore.rebuildHome(userId, rebuilt) }
        return rebuilt.filter { beforeScore == null || it.score < beforeScore }.sortedByDescending { it.score }.take(limit)
    }

    private fun readAuthor(authorId: Long, beforeScore: Long?, limit: Int): List<TimelineEntry> {
        val slice = safely("작성자 읽기") { timelineStore.readAuthor(authorId, beforeScore, limit) } ?: MISSING
        if (slice.exists) return slice.entries
        safely("작성자 재구축 시작") { timelineStore.beginRebuildAuthor(authorId) }
        val rebuilt = fanoutService.entriesOf(postCandidateRepository.findRecentPostIdsByAuthor(authorId, TimelinePolicy.AUTHOR_TIMELINE_SIZE))
        safely("작성자 재구축 저장") { timelineStore.rebuildAuthor(authorId, rebuilt) }
        return rebuilt.filter { beforeScore == null || it.score < beforeScore }.sortedByDescending { it.score }.take(limit)
    }

    private fun <T> safely(what: String, action: () -> T): T? = try {
        action()
    } catch (e: RuntimeException) {
        logger.warn("타임라인 캐시 {} 실패 — PostgreSQL 로 진행", what, e)
        null
    }

    private companion object {
        val MISSING = TimelineSlice(exists = false, entries = emptyList())
        val logger = LoggerFactory.getLogger(FollowingLatestCandidateSource::class.java)
    }
}

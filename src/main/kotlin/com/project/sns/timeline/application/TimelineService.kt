package com.project.sns.timeline.application

import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.timeline.domain.TimelineCandidateFilter
import com.project.sns.timeline.domain.TimelineCandidateSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TimelineService(
    private val sources: List<TimelineCandidateSource>,
    private val filters: List<TimelineCandidateFilter>,
    private val hydrator: TimelineHydrator,
) {
    @Transactional(readOnly = true)
    fun read(userId: Long, cursor: Long?, limit: Int): TimelinePage {
        val pageLimit = limit.coerceIn(1, TimelinePolicy.PAGE_MAX_LIMIT)
        val fetch = pageLimit + pageLimit / 2 + 1

        val entries = sources.flatMap { it.collect(userId, cursor, fetch) }.distinctBy { it.postId }
            .sortedByDescending { it.score }
        val candidates = entries.map { it.postId }
        val active = hydrator.loadActive(candidates)
        val accepted = filters.fold(active) { posts, filter -> filter.filter(userId, posts) }
        val items = hydrator.toItems(accepted).take(pageLimit)

        val consumedUpTo = if (items.size < pageLimit) candidates.size - 1 else candidates.indexOf(items.last().post.id)
        val exhausted = consumedUpTo == candidates.size - 1 && candidates.size < fetch
        val nextCursor = if (exhausted) null else entries.getOrNull(consumedUpTo)?.score
        return TimelinePage(items = items, nextCursor = nextCursor)
    }
}

data class TimelinePage(
    val items: List<TimelineItem>,
    val nextCursor: Long?,
)

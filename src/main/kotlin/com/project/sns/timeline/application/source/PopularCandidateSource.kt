package com.project.sns.timeline.application.source

import com.project.sns.timeline.application.TimelineFanoutService
import com.project.sns.timeline.domain.PostCandidateRepository
import com.project.sns.timeline.domain.TimelineCandidateSource
import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.timeline.domain.TimelineEntry
import java.time.Instant
import org.springframework.core.annotation.Order

@Order(1)
class PopularCandidateSource(
    private val postCandidateRepository: PostCandidateRepository,
    private val fanoutService: TimelineFanoutService,
) : TimelineCandidateSource {
    override fun collect(userId: Long, beforeScore: Long?, limit: Int): List<TimelineEntry> {
        val ids = postCandidateRepository.findPopularPostIds(
            Instant.now().minus(TimelinePolicy.POPULAR_WINDOW),
            TimelinePolicy.POPULAR_MIN_LIKES,
            beforeScore,
            limit
        )
        return fanoutService.entriesOf(ids)
    }
}

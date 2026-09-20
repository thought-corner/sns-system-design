package com.project.sns.timeline.application.filter

import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.post.application.PostDetail
import com.project.sns.timeline.domain.TimelineCandidateFilter
import java.time.Instant
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class StaleContentFilter : TimelineCandidateFilter {
    override fun filter(userId: Long, candidates: List<PostDetail>): List<PostDetail> {
        val cutoff = Instant.now().minus(TimelinePolicy.STALE_MAX_AGE)
        return candidates.filter { it.createdAt.isAfter(cutoff) }
    }
}

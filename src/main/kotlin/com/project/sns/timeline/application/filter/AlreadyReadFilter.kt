package com.project.sns.timeline.application.filter

import com.project.sns.timeline.domain.TimelinePolicy
import com.project.sns.post.application.PostDetail
import com.project.sns.post.domain.PostViewRepository
import com.project.sns.timeline.domain.TimelineCandidateFilter
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(0)
class AlreadyReadFilter(
    private val postViewRepository: PostViewRepository,
) : TimelineCandidateFilter {
    override fun filter(userId: Long, candidates: List<PostDetail>): List<PostDetail> {
        if (!TimelinePolicy.ALREADY_READ_FILTER_ENABLED || candidates.isEmpty()) return candidates
        val viewed = postViewRepository.findViewedPostIds(userId, candidates.map { it.repostOfId ?: it.id }.distinct())
        return candidates.filterNot { (it.repostOfId ?: it.id) in viewed }
    }
}

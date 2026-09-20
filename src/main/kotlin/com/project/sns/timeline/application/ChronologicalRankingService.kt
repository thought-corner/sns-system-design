package com.project.sns.timeline.application

import com.project.sns.timeline.domain.RankingService
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class ChronologicalRankingService : RankingService {
    override fun score(postId: Long, createdAt: Instant): Long = postId
}

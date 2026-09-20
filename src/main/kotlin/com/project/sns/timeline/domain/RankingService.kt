package com.project.sns.timeline.domain

import java.time.Instant

fun interface RankingService {
    fun score(postId: Long, createdAt: Instant): Long
}

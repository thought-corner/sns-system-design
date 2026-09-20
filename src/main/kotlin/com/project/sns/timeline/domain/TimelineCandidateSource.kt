package com.project.sns.timeline.domain

fun interface TimelineCandidateSource {
    fun collect(userId: Long, beforeScore: Long?, limit: Int): List<TimelineEntry>
}

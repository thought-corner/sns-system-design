package com.project.sns.timeline.domain

import com.project.sns.post.application.PostDetail

fun interface TimelineCandidateFilter {
    fun filter(userId: Long, candidates: List<PostDetail>): List<PostDetail>
}

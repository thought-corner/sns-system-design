package com.project.sns.timeline.domain

import java.time.Instant

interface PostCandidateRepository {
    fun findRecentPostIdsByAuthor(authorId: Long, limit: Int): List<Long>

    fun findRecentPostIdsByAuthors(authorIds: Collection<Long>, limit: Int): List<Long>

    fun findPopularPostIds(since: Instant, minLikes: Long, beforeId: Long?, limit: Int): List<Long>
}

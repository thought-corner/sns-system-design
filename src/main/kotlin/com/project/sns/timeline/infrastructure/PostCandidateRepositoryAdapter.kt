package com.project.sns.timeline.infrastructure

import com.project.sns.timeline.domain.PostCandidateRepository
import java.time.Instant
import org.springframework.stereotype.Repository

@Repository
class PostCandidateRepositoryAdapter(
    private val jpaRepository: SpringDataTimelineQueryRepository,
) : PostCandidateRepository {
    override fun findRecentPostIdsByAuthor(authorId: Long, limit: Int): List<Long> =
        jpaRepository.findRecentPostIdsByAuthor(authorId, limit)

    override fun findRecentPostIdsByAuthors(authorIds: Collection<Long>, limit: Int): List<Long> =
        if (authorIds.isEmpty()) emptyList() else jpaRepository.findRecentPostIdsByAuthors(authorIds, limit)

    override fun findPopularPostIds(since: Instant, minLikes: Long, beforeId: Long?, limit: Int): List<Long> =
        jpaRepository.findPopularPostIds(since, minLikes, beforeId, limit)
}

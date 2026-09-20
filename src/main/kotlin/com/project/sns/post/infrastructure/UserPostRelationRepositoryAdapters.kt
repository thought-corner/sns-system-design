package com.project.sns.post.infrastructure

import com.project.sns.post.domain.PostLikeRepository
import com.project.sns.post.domain.PostViewRepository
import org.springframework.stereotype.Repository

@Repository
class PostLikeRepositoryAdapter(
    private val jpaRepository: SpringDataPostLikeJpaRepository,
) : PostLikeRepository {
    override fun create(userId: Long, postId: Long): Boolean = jpaRepository.insertIfAbsent(userId, postId) == 1

    override fun softDelete(userId: Long, postId: Long): Boolean = jpaRepository.softDeleteRelation(userId, postId) == 1
}

@Repository
class PostViewRepositoryAdapter(
    private val jpaRepository: SpringDataPostViewJpaRepository,
) : PostViewRepository {
    override fun record(userId: Long, postId: Long): Boolean = jpaRepository.insertIfAbsent(userId, postId) == 1

    override fun findViewedPostIds(userId: Long, postIds: Collection<Long>): Set<Long> =
        if (postIds.isEmpty()) emptySet() else jpaRepository.findViewedPostIds(userId, postIds).toSet()
}

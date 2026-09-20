package com.project.sns.post.domain

interface PostCountsRepository {
    fun get(postId: Long): PostCounts

    fun getAll(postIds: Collection<Long>): Map<Long, PostCounts>

    fun increase(postId: Long, delta: PostCountDelta)

    fun decrease(postId: Long, delta: PostCountDelta)
}

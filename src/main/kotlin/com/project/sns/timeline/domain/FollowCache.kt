package com.project.sns.timeline.domain

interface FollowCache {
    fun followingIds(userId: Long): Set<Long>?

    fun putFollowingIds(userId: Long, ids: Collection<Long>)

    fun evictFollowingIds(userId: Long)

    fun celebrityIds(userId: Long): Set<Long>?

    fun putCelebrityIds(userId: Long, ids: Collection<Long>)

    fun evictCelebrityIds(userId: Long)
}

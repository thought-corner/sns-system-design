package com.project.sns.timeline.domain

interface SocialGraph {
    fun followingIds(userId: Long): List<Long>

    fun followerIds(followingId: Long, afterFollowerId: Long, limit: Int): List<Long>

    fun followerCount(userId: Long): Long

    fun followedCelebrityIds(userId: Long, celebrityThreshold: Long): List<Long>
}

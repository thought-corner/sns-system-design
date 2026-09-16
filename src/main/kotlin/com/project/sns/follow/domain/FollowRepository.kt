package com.project.sns.follow.domain

interface FollowRepository {
    fun create(followerId: Long, followingId: Long): Boolean

    fun softDelete(followerId: Long, followingId: Long): Boolean

    /** 관계가 실제로 생성된 뒤에만 호출한다 — 대상의 팔로워 수, 행위자의 팔로잉 수 +1. */
    fun increaseCounts(followerId: Long, followingId: Long)

    /** 관계가 실제로 소프트 삭제된 뒤에만 호출한다 — 대상의 팔로워 수, 행위자의 팔로잉 수 -1. */
    fun decreaseCounts(followerId: Long, followingId: Long)

    /** 행이 없으면 0 으로 채운 값을 돌려준다. */
    fun getCounts(userId: Long): FollowCounts
}

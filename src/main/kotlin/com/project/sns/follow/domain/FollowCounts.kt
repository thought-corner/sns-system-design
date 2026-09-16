package com.project.sns.follow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 사용자별 팔로워 수·팔로잉 수 카운터(`follow_counts`). 활성 관계의 COUNT 와 같아야 하며,
 * 관계 삽입·소프트 삭제가 실제로 일어난 같은 트랜잭션에서만 증감된다(설계 문서 "2. 팔로워 수와 팔로잉 수" 개정).
 * 행이 없는 사용자는 0 으로 읽는다.
 */
@Entity
@Table(name = "follow_counts")
class FollowCounts(
    @Id
    @Column(name = "user_id")
    val userId: Long,
    @Column(name = "follower_count", nullable = false)
    val followerCount: Long = 0,
    @Column(name = "following_count", nullable = false)
    val followingCount: Long = 0,
)

package com.project.sns.timeline.domain

import java.time.Duration

object TimelinePolicy {
    const val HOME_TIMELINE_SIZE = 800
    const val AUTHOR_TIMELINE_SIZE = 200
    val IDLE_TTL: Duration = Duration.ofDays(7)
    val REBUILD_SENTINEL_TTL: Duration = Duration.ofSeconds(60)
    const val BACKFILL_SIZE = 50
    const val PAGE_MAX_LIMIT = 50
    const val CELEBRITY_MAX_FOLLOWERS = 10_000L
    val FOLLOW_CACHE_TTL: Duration = Duration.ofMinutes(10)
    val POST_CACHE_TTL: Duration = Duration.ofMinutes(10)
    val STALE_MAX_AGE: Duration = Duration.ofDays(30)
    const val ALREADY_READ_FILTER_ENABLED = true
    const val POPULAR_SOURCE_ENABLED = false
    const val POPULAR_MIN_LIKES = 10L
    val POPULAR_WINDOW: Duration = Duration.ofDays(7)

    object Fanout {
        const val FOLLOWER_PAGE_SIZE = 1_000
        const val STREAM_MAX_LENGTH = 100_000L
        const val POLL_COUNT = 32
        val POLL_BLOCK: Duration = Duration.ofSeconds(5)
        val CLAIM_MIN_IDLE: Duration = Duration.ofSeconds(60)
        const val MAX_DELIVERIES = 3L
    }
}

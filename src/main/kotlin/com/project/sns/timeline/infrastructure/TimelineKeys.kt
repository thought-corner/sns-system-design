package com.project.sns.timeline.infrastructure

object TimelineKeys {
    const val FANOUT_STREAM = "sns:timeline:fanout"
    const val FANOUT_GROUP = "timeline-fanout"

    fun home(userId: Long): String = "sns:timeline:home:$userId"

    fun author(authorId: Long): String = "sns:timeline:author:$authorId"

    fun following(userId: Long): String = "sns:timeline:following:$userId"

    fun celebrities(userId: Long): String = "sns:timeline:celebs:$userId"

    fun post(postId: Long): String = "sns:timeline:post:$postId"
}

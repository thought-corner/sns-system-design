package com.project.sns.timeline.domain

data class TimelineEntry(
    val postId: Long,
    val score: Long,
)

data class TimelineSlice(
    val exists: Boolean,
    val entries: List<TimelineEntry>,
)

interface TimelineStore {
    fun pushHome(userIds: Collection<Long>, entry: TimelineEntry)

    fun pushAuthor(authorId: Long, entry: TimelineEntry)

    fun removeHome(userId: Long, postIds: Collection<Long>)

    fun readHome(userId: Long, beforeScore: Long?, limit: Int): TimelineSlice

    fun readAuthor(authorId: Long, beforeScore: Long?, limit: Int): TimelineSlice

    fun beginRebuildHome(userId: Long)

    fun rebuildHome(userId: Long, entries: List<TimelineEntry>)

    fun beginRebuildAuthor(authorId: Long)

    fun rebuildAuthor(authorId: Long, entries: List<TimelineEntry>)
}

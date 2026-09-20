package com.project.sns.timeline.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

class PostSnapshot @JsonCreator constructor(
    @JsonProperty("id") val id: Long,
    @JsonProperty("authorId") val authorId: Long,
    @JsonProperty("content") val content: String,
    @JsonProperty("parentPostId") val parentPostId: Long?,
    @JsonProperty("quotedPostId") val quotedPostId: Long?,
    @JsonProperty("repostOfId") val repostOfId: Long?,
    @JsonProperty("createdAt") val createdAt: Instant,
    @JsonProperty("media") val media: List<MediaSnapshot>,
)

class MediaSnapshot @JsonCreator constructor(
    @JsonProperty("id") val id: Long,
    @JsonProperty("contentType") val contentType: String,
    @JsonProperty("sizeBytes") val sizeBytes: Long,
    @JsonProperty("width") val width: Int?,
    @JsonProperty("height") val height: Int?,
    @JsonProperty("storageKey") val storageKey: String,
)

interface PostCache {
    fun getAll(ids: Collection<Long>): Map<Long, PostSnapshot>

    fun putAll(snapshots: Collection<PostSnapshot>)

    fun evict(id: Long)
}

package com.project.sns.media.domain

interface MediaRepository {
    fun save(media: Media): Media

    fun findActiveByIdAndOwner(id: Long, ownerId: Long): Media?

    fun findAttached(postId: Long): List<Media>

    fun findAttachedIn(postIds: Collection<Long>): List<Media>

    fun markReady(id: Long, width: Int?, height: Int?): Boolean

    fun attach(id: Long, ownerId: Long, postId: Long, position: Int): Boolean

    fun softDeletePending(id: Long): Boolean
}

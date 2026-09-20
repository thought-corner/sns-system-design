package com.project.sns.media.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class MediaStatus {
    PENDING,
    READY,
}

@Entity
@Table(name = "media")
class Media(
    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,

    @Column(name = "storage_key", nullable = false, length = 255)
    val storageKey: String,

    @Column(name = "content_type", nullable = false, length = 64)
    val contentType: String,

    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val status: MediaStatus = MediaStatus.PENDING,

    @Column(name = "post_id")
    val postId: Long? = null,

    @Column
    val position: Int? = null,

    @Column
    val width: Int? = null,

    @Column
    val height: Int? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "ready_at")
    val readyAt: Instant? = null,

    @Column(name = "deleted_at")
    val deletedAt: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

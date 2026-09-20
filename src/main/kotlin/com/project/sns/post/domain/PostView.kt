package com.project.sns.post.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

@Entity
@Table(name = "post_views")
class PostView(
    @EmbeddedId
    val id: PostViewId,

    @Column(name = "viewed_at", nullable = false)
    val viewedAt: Instant = Instant.now(),
)

@Embeddable
data class PostViewId(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
) : Serializable

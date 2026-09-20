package com.project.sns.post.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "posts")
class Post(
    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Column(nullable = false, length = 500)
    val content: String,

    @Column(name = "parent_post_id")
    val parentPostId: Long? = null,

    @Column(name = "quoted_post_id")
    val quotedPostId: Long? = null,

    @Column(name = "repost_of_id")
    val repostOfId: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    val deletedAt: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

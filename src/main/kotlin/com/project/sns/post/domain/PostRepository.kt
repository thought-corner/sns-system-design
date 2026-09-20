package com.project.sns.post.domain

interface PostRepository {
    fun save(post: Post): Post

    fun findActiveById(id: Long): Post?

    /** 활성 행을 공유 잠금(`FOR SHARE`)으로 읽는다 — 쓰기 경로의 대상 확인이 소프트 삭제와 직렬화되도록. 트랜잭션 안에서만 호출한다. */
    fun findActiveByIdForShare(id: Long): Post?

    fun findById(id: Long): Post?

    fun findActiveByIds(ids: Collection<Long>): List<Post>

    fun findActiveIds(ids: Collection<Long>): Set<Long>

    fun softDelete(postId: Long): Boolean

    fun createRepost(authorId: Long, originalId: Long): Boolean

    fun findActiveRepostId(authorId: Long, originalId: Long): Long?

    fun softDeleteRepost(authorId: Long, originalId: Long): Boolean

    fun softDeleteRepostsOf(originalId: Long): Int
}

interface UserPostRelationRepository {
    fun create(userId: Long, postId: Long): Boolean

    fun softDelete(userId: Long, postId: Long): Boolean
}

interface PostLikeRepository : UserPostRelationRepository

interface PostViewRepository {
    fun record(userId: Long, postId: Long): Boolean

    fun findViewedPostIds(userId: Long, postIds: Collection<Long>): Set<Long>
}

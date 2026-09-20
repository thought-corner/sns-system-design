package com.project.sns.post.infrastructure

import com.project.sns.post.domain.PostView
import com.project.sns.post.domain.PostViewId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringDataPostViewJpaRepository : JpaRepository<PostView, PostViewId> {
    @Modifying
    @Query(
        value = """
            INSERT INTO post_views (post_id, user_id, viewed_at)
            VALUES (:postId, :userId, CURRENT_TIMESTAMP)
            ON CONFLICT (post_id, user_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("userId") userId: Long, @Param("postId") postId: Long): Int

    @Query("SELECT view.id.postId FROM PostView view WHERE view.id.userId = :userId AND view.id.postId IN :postIds")
    fun findViewedPostIds(@Param("userId") userId: Long, @Param("postIds") postIds: Collection<Long>): List<Long>

    @Query("SELECT COUNT(view) FROM PostView view WHERE view.id.postId = :postId")
    fun countByPostId(@Param("postId") postId: Long): Long
}

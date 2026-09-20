package com.project.sns.post.application

import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostLikeRepository
import com.project.sns.post.domain.PostNotFoundException
import com.project.sns.post.domain.PostRepository
import com.project.sns.post.domain.PostViewRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostEngagementServiceTests {
    private val postService = mock(PostService::class.java)
    private val postRepository = mock(PostRepository::class.java)
    private val postLikeRepository = mock(PostLikeRepository::class.java)
    private val postViewRepository = mock(PostViewRepository::class.java)

    private val repostService = RepostService(postService, postRepository)
    private val likeService = LikeService(postService, postRepository, postLikeRepository)
    private val viewService = ViewService(postService, postRepository, postViewRepository)

    @Test
    fun `처음 리포스트하면 리포스트 수를 올린다`() {
        doReturn(post()).`when`(postService).resolveTarget(POST_ID)
        doReturn(true).`when`(postRepository).createRepost(USER_ID, POST_ID)

        assertTrue(repostService.repost(USER_ID, POST_ID).changed)
        verify(postRepository).increaseCounts(POST_ID, PostCountDelta.REPOST)
    }

    @Test
    fun `이미 리포스트한 게시글은 변경 없이 성공한다`() {
        doReturn(post()).`when`(postService).resolveTarget(POST_ID)
        doReturn(false).`when`(postRepository).createRepost(USER_ID, POST_ID)

        assertFalse(repostService.repost(USER_ID, POST_ID).changed)
        verify(postRepository, never()).increaseCounts(POST_ID, PostCountDelta.REPOST)
    }

    @Test
    fun `리포스트 취소는 활성 리포스트 행이 있었을 때만 리포스트 수를 내린다`() {
        doReturn(post()).`when`(postService).resolveTarget(POST_ID)
        doReturn(true).`when`(postRepository).softDeleteRepost(USER_ID, POST_ID)

        assertTrue(repostService.undoRepost(USER_ID, POST_ID).changed)
        verify(postRepository).decreaseCounts(POST_ID, PostCountDelta.REPOST)
    }

    @Test
    fun `이미 취소된 리포스트는 리포스트 수를 내리지 않는다`() {
        doReturn(post()).`when`(postService).resolveTarget(POST_ID)
        doReturn(false).`when`(postRepository).softDeleteRepost(USER_ID, POST_ID)

        assertFalse(repostService.undoRepost(USER_ID, POST_ID).changed)
        verify(postRepository, never()).decreaseCounts(POST_ID, PostCountDelta.REPOST)
    }

    @Test
    fun `좋아요 취소는 활성 관계가 있었을 때만 좋아요 수를 내린다`() {
        doReturn(post()).`when`(postService).resolveTarget(POST_ID)
        doReturn(true).`when`(postLikeRepository).softDelete(USER_ID, POST_ID)

        assertTrue(likeService.unlike(USER_ID, POST_ID).changed)
        verify(postRepository).decreaseCounts(POST_ID, PostCountDelta.LIKE)
    }

    @Test
    fun `같은 사용자의 재조회는 조회수를 올리지 않는다`() {
        doReturn(post()).`when`(postService).resolveTarget(POST_ID)
        doReturn(false).`when`(postViewRepository).record(USER_ID, POST_ID)

        assertFalse(viewService.view(USER_ID, POST_ID).changed)
        verify(postRepository, never()).increaseCounts(POST_ID, PostCountDelta.VIEW)
    }

    @Test
    fun `리포스트 행에 대한 좋아요는 원본에 귀속된다`() {
        doReturn(Post(authorId = 99L, content = "원본", id = ORIGINAL_ID)).`when`(postService).resolveTarget(REPOST_ROW_ID)
        doReturn(true).`when`(postLikeRepository).create(USER_ID, ORIGINAL_ID)

        assertTrue(likeService.like(USER_ID, REPOST_ROW_ID).changed)
        verify(postRepository).increaseCounts(ORIGINAL_ID, PostCountDelta.LIKE)
        verify(postLikeRepository, never()).create(USER_ID, REPOST_ROW_ID)
    }

    @Test
    fun `삭제된 게시글에는 좋아요할 수 없다`() {
        doThrow(PostNotFoundException()).`when`(postService).resolveTarget(POST_ID)

        assertFailsWith<PostNotFoundException> { likeService.like(USER_ID, POST_ID) }
        verifyNoInteractions(postLikeRepository)
    }

    private fun post() = Post(authorId = 99L, content = "본문", id = POST_ID)

    private companion object {
        const val USER_ID = 1L
        const val POST_ID = 10L
        const val ORIGINAL_ID = 20L
        const val REPOST_ROW_ID = 21L
    }
}

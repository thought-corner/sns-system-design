package com.project.sns.post.application

import com.project.sns.media.application.MediaAttachmentService
import com.project.sns.media.application.MediaDetail
import com.project.sns.media.application.MediaViewService
import com.project.sns.media.domain.MediaNotReadyException
import com.project.sns.post.domain.NotPostAuthorException
import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostCountDelta
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostCountsRepository
import com.project.sns.post.domain.PostNotFoundException
import com.project.sns.post.domain.PostRepository
import com.project.sns.timeline.application.PostReadService
import com.project.sns.timeline.application.TimelineFanoutPublisher
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostServiceTests {
    private val postRepository = mock(PostRepository::class.java)
    private val postCountsRepository = mock(PostCountsRepository::class.java)
    private val postTargetResolver = mock(PostTargetResolver::class.java)
    private val mediaAttachmentService = mock(MediaAttachmentService::class.java)
    private val mediaViewService = mock(MediaViewService::class.java)
    private val timelineFanoutPublisher = mock(TimelineFanoutPublisher::class.java)
    private val postReadService = mock(PostReadService::class.java)
    private val postService =
        PostService(
            postRepository,
            postCountsRepository,
            postTargetResolver,
            mediaAttachmentService,
            mediaViewService,
            timelineFanoutPublisher,
            postReadService
        )

    @Test
    fun `작성 직후의 게시글은 통계를 조회하지 않고 0 으로 응답한다`() {
        doReturn(post(POST_ID, AUTHOR_ID)).`when`(postRepository)
            .save(any(Post::class.java) ?: post(POST_ID, AUTHOR_ID))

        val detail = postService.create(AUTHOR_ID, "본문")

        assertEquals(POST_ID, detail.id)
        assertEquals(0L, detail.counts.replyCount)
        assertEquals(0L, detail.counts.viewCount)
        verify(postCountsRepository, never()).get(POST_ID)
    }

    @Test
    fun `mediaIds 가 있으면 작성 트랜잭션 안에서 같은 순서로 첨부하고 응답에 싣는다`() {
        doReturn(post(POST_ID, AUTHOR_ID)).`when`(postRepository)
            .save(any(Post::class.java) ?: post(POST_ID, AUTHOR_ID))
        doReturn(listOf(mediaDetail(31L), mediaDetail(30L))).`when`(mediaViewService).listForPost(POST_ID)

        val detail = postService.create(AUTHOR_ID, "본문", listOf(31L, 30L))

        verify(mediaAttachmentService).attach(AUTHOR_ID, POST_ID, listOf(31L, 30L))
        assertEquals(listOf(31L, 30L), detail.media.map { it.id })
    }

    @Test
    fun `첨부에 실패하면 예외가 그대로 나가 게시글 작성이 롤백된다`() {
        doReturn(post(POST_ID, AUTHOR_ID)).`when`(postRepository)
            .save(any(Post::class.java) ?: post(POST_ID, AUTHOR_ID))
        doThrow(MediaNotReadyException()).`when`(mediaAttachmentService).attach(AUTHOR_ID, POST_ID, listOf(31L))

        assertFailsWith<MediaNotReadyException> { postService.create(AUTHOR_ID, "본문", listOf(31L)) }
    }

    @Test
    fun `작성자가 답글을 삭제하면 원본의 답글 수를 내린다`() {
        doReturn(post(REPLY_ID, AUTHOR_ID, parentPostId = PARENT_ID)).`when`(postRepository).findById(REPLY_ID)
        doReturn(true).`when`(postRepository).softDelete(REPLY_ID)

        val result = postService.delete(AUTHOR_ID, REPLY_ID)

        assertTrue(result.changed)
        verify(postCountsRepository).decrease(PARENT_ID, PostCountDelta.REPLY)
    }

    @Test
    fun `작성자가 인용을 삭제하면 원본의 인용 수를 내린다`() {
        doReturn(post(QUOTE_ID, AUTHOR_ID, quotedPostId = PARENT_ID)).`when`(postRepository).findById(QUOTE_ID)
        doReturn(true).`when`(postRepository).softDelete(QUOTE_ID)

        assertTrue(postService.delete(AUTHOR_ID, QUOTE_ID).changed)
        verify(postCountsRepository).decrease(PARENT_ID, PostCountDelta.QUOTE)
        verify(postCountsRepository, never()).decrease(PARENT_ID, PostCountDelta.REPLY)
    }

    @Test
    fun `원본을 삭제하면 활성 리포스트 행도 함께 삭제되고 리포스트 수가 그만큼 내려간다`() {
        doReturn(post(POST_ID, AUTHOR_ID)).`when`(postRepository).findById(POST_ID)
        doReturn(true).`when`(postRepository).softDelete(POST_ID)
        doReturn(3).`when`(postRepository).softDeleteRepostsOf(POST_ID)

        assertTrue(postService.delete(AUTHOR_ID, POST_ID).changed)
        verify(postCountsRepository).decrease(POST_ID, PostCountDelta(repost = 3))
    }

    @Test
    fun `리포스트 행을 삭제할 때는 캐스케이드하지 않는다`() {
        doReturn(
            Post(
                authorId = AUTHOR_ID,
                content = "",
                repostOfId = PARENT_ID,
                id = REPOST_ID
            )
        ).`when`(postRepository).findById(REPOST_ID)
        doReturn(true).`when`(postRepository).softDelete(REPOST_ID)

        postService.delete(AUTHOR_ID, REPOST_ID)
        verify(postRepository, never()).softDeleteRepostsOf(REPOST_ID)
    }

    @Test
    fun `작성자가 리포스트 행을 삭제하면 원본의 리포스트 수를 내린다`() {
        doReturn(
            Post(
                authorId = AUTHOR_ID,
                content = "",
                repostOfId = PARENT_ID,
                id = REPOST_ID
            )
        ).`when`(postRepository).findById(REPOST_ID)
        doReturn(true).`when`(postRepository).softDelete(REPOST_ID)

        assertTrue(postService.delete(AUTHOR_ID, REPOST_ID).changed)
        verify(postCountsRepository).decrease(PARENT_ID, PostCountDelta.REPOST)
    }

    @Test
    fun `리포스트를 가진 답글을 삭제하면 원본의 답글 수와 자기 리포스트 수가 한 번에 내려간다`() {
        doReturn(post(REPLY_ID, AUTHOR_ID, parentPostId = PARENT_ID)).`when`(postRepository).findById(REPLY_ID)
        doReturn(true).`when`(postRepository).softDelete(REPLY_ID)
        doReturn(2).`when`(postRepository).softDeleteRepostsOf(REPLY_ID)

        assertTrue(postService.delete(AUTHOR_ID, REPLY_ID).changed)

        val order = inOrder(postRepository, postCountsRepository)
        order.verify(postCountsRepository).decrease(PARENT_ID, PostCountDelta.REPLY)
        order.verify(postRepository).softDeleteRepostsOf(REPLY_ID)
        order.verify(postCountsRepository).decrease(REPLY_ID, PostCountDelta(repost = 2))
    }

    @Test
    fun `이미 삭제된 게시글을 작성자가 다시 지우면 변경 없이 성공한다`() {
        doReturn(post(REPLY_ID, AUTHOR_ID, parentPostId = PARENT_ID)).`when`(postRepository).findById(REPLY_ID)
        doReturn(false).`when`(postRepository).softDelete(REPLY_ID)

        val result = postService.delete(AUTHOR_ID, REPLY_ID)

        assertFalse(result.changed)
        verify(postCountsRepository, never()).decrease(PARENT_ID, PostCountDelta.REPLY)
    }

    @Test
    fun `작성자가 아니면 삭제할 수 없다`() {
        doReturn(post(POST_ID, AUTHOR_ID)).`when`(postRepository).findById(POST_ID)

        assertFailsWith<NotPostAuthorException> { postService.delete(OTHER_ID, POST_ID) }
        verify(postRepository, never()).softDelete(POST_ID)
    }

    @Test
    fun `없는 게시글은 삭제할 수 없다`() {
        doReturn(null).`when`(postRepository).findById(POST_ID)

        assertFailsWith<PostNotFoundException> { postService.delete(AUTHOR_ID, POST_ID) }
    }

    @Test
    fun `삭제된 게시글은 조회되지 않는다`() {
        doThrow(PostNotFoundException()).`when`(postTargetResolver).getActive(POST_ID)

        assertFailsWith<PostNotFoundException> { postService.get(POST_ID) }
        verify(postCountsRepository, never()).get(POST_ID)
    }

    @Test
    fun `통계 행이 없는 게시글은 0 으로 조회된다`() {
        doReturn(post(POST_ID, AUTHOR_ID)).`when`(postTargetResolver).getActive(POST_ID)
        doReturn(PostCounts(postId = POST_ID)).`when`(postCountsRepository).get(POST_ID)

        val detail = postService.get(POST_ID)

        assertEquals(0L, detail.counts.replyCount)
        assertEquals(0L, detail.counts.viewCount)
    }

    private fun mediaDetail(id: Long) = MediaDetail(
        id = id,
        contentType = "image/png",
        sizeBytes = 10L,
        width = 1,
        height = 1,
        url = java.net.URL("https://media.test/download/$id"),
    )

    private fun post(id: Long, authorId: Long, parentPostId: Long? = null, quotedPostId: Long? = null) = Post(
        authorId = authorId,
        content = "본문",
        parentPostId = parentPostId,
        quotedPostId = quotedPostId,
        id = id,
    )

    private companion object {
        const val AUTHOR_ID = 1L
        const val OTHER_ID = 2L
        const val POST_ID = 10L
        const val PARENT_ID = 11L
        const val REPLY_ID = 12L
        const val QUOTE_ID = 13L
        const val REPOST_ID = 14L
    }
}

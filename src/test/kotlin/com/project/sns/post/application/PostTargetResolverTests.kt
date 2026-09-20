package com.project.sns.post.application

import com.project.sns.post.domain.Post
import com.project.sns.post.domain.PostNotFoundException
import com.project.sns.post.domain.PostRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PostTargetResolverTests {
    private val postRepository = mock(PostRepository::class.java)
    private val resolver = PostTargetResolver(postRepository)

    @Test
    fun `리포스트 행을 대상으로 지목하면 원본으로 해석된다`() {
        doReturn(repostRow()).`when`(postRepository).findActiveById(REPOST_ID)
        doReturn(post(POST_ID)).`when`(postRepository).findActiveById(POST_ID)
        doReturn(post(POST_ID)).`when`(postRepository).findActiveByIdForShare(POST_ID)

        assertEquals(POST_ID, resolver.resolveTarget(REPOST_ID).id)
        assertEquals(POST_ID, resolver.resolveTarget(POST_ID).id)
    }

    @Test
    fun `대상 확인은 최종 대상만 공유 잠금으로 다시 읽고 리포스트 행은 잠그지 않는다`() {
        doReturn(repostRow()).`when`(postRepository).findActiveById(REPOST_ID)
        doReturn(post(POST_ID)).`when`(postRepository).findActiveByIdForShare(POST_ID)

        resolver.resolveTarget(REPOST_ID)

        verify(postRepository).findActiveByIdForShare(POST_ID)
        verify(postRepository, never()).findActiveByIdForShare(REPOST_ID)
    }

    @Test
    fun `원본이 삭제된 리포스트 행은 대상이 될 수 없다`() {
        doReturn(repostRow()).`when`(postRepository).findActiveById(REPOST_ID)
        doReturn(null).`when`(postRepository).findActiveByIdForShare(POST_ID)

        assertFailsWith<PostNotFoundException> { resolver.resolveTarget(REPOST_ID) }
    }

    @Test
    fun `잠금 없이 읽은 뒤 삭제된 대상은 공유 잠금 재확인에서 404 가 된다`() {
        doReturn(post(POST_ID)).`when`(postRepository).findActiveById(POST_ID)
        doReturn(null).`when`(postRepository).findActiveByIdForShare(POST_ID)

        assertFailsWith<PostNotFoundException> { resolver.resolveTarget(POST_ID) }
    }

    @Test
    fun `getActive 는 잠그지 않고 리포스트 행이면 그 행 자체를 돌려준다`() {
        doReturn(repostRow()).`when`(postRepository).findActiveById(REPOST_ID)

        assertEquals(REPOST_ID, resolver.getActive(REPOST_ID).id)
        verify(postRepository, never()).findActiveByIdForShare(REPOST_ID)
        verify(postRepository, never()).findActiveByIdForShare(POST_ID)
    }

    @Test
    fun `삭제된 게시글은 조회되지 않는다`() {
        doReturn(null).`when`(postRepository).findActiveById(POST_ID)

        assertFailsWith<PostNotFoundException> { resolver.getActive(POST_ID) }
    }

    private fun post(id: Long) = Post(authorId = AUTHOR_ID, content = "본문", id = id)

    private fun repostRow() = Post(authorId = OTHER_ID, content = "", repostOfId = POST_ID, id = REPOST_ID)

    private companion object {
        const val AUTHOR_ID = 1L
        const val OTHER_ID = 2L
        const val POST_ID = 10L
        const val REPOST_ID = 14L
    }
}

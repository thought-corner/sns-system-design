package com.project.sns.media.application

import com.project.sns.media.domain.Media
import com.project.sns.media.domain.MediaAlreadyAttachedException
import com.project.sns.media.domain.MediaNotFoundException
import com.project.sns.media.domain.MediaNotReadyException
import com.project.sns.media.domain.MediaRepository
import com.project.sns.media.domain.MediaStatus
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.assertFailsWith

class MediaAttachmentServiceTests {
    private val mediaRepository = mock(MediaRepository::class.java)
    private val service = MediaAttachmentService(mediaRepository)

    @Test
    fun `빈 목록이면 아무것도 하지 않는다`() {
        service.attach(OWNER_ID, POST_ID, emptyList())
        verifyNoInteractions(mediaRepository)
    }

    @Test
    fun `position 은 요청 순서, UPDATE 는 id 오름차순으로 실행한다`() {
        doReturn(true).`when`(mediaRepository).attach(31L, OWNER_ID, POST_ID, 0)
        doReturn(true).`when`(mediaRepository).attach(30L, OWNER_ID, POST_ID, 1)

        service.attach(OWNER_ID, POST_ID, listOf(31L, 30L))

        val order = inOrder(mediaRepository)
        order.verify(mediaRepository).attach(30L, OWNER_ID, POST_ID, 1)
        order.verify(mediaRepository).attach(31L, OWNER_ID, POST_ID, 0)
    }

    @Test
    fun `UPDATE 가 0 행이면 이유를 조회해 404·409 로 나눈다`() {
        doReturn(false).`when`(mediaRepository).attach(anyLong(), anyLong(), anyLong(), anyInt())

        doReturn(null).`when`(mediaRepository).findActiveByIdAndOwner(1L, OWNER_ID)
        assertFailsWith<MediaNotFoundException> { service.attach(OWNER_ID, POST_ID, listOf(1L)) }

        doReturn(ready(2L).copy(status = MediaStatus.PENDING)).`when`(mediaRepository)
            .findActiveByIdAndOwner(2L, OWNER_ID)
        assertFailsWith<MediaNotReadyException> { service.attach(OWNER_ID, POST_ID, listOf(2L)) }

        doReturn(ready(3L, postId = 99L, position = 0)).`when`(mediaRepository).findActiveByIdAndOwner(3L, OWNER_ID)
        assertFailsWith<MediaAlreadyAttachedException> { service.attach(OWNER_ID, POST_ID, listOf(3L)) }

    }

    @Test
    fun `두 번째 미디어에서 실패하면 예외가 나가 첫 번째 첨부도 트랜잭션과 함께 롤백된다`() {
        doReturn(true).`when`(mediaRepository).attach(30L, OWNER_ID, POST_ID, 0)
        doReturn(false).`when`(mediaRepository).attach(31L, OWNER_ID, POST_ID, 1)
        doReturn(null).`when`(mediaRepository).findActiveByIdAndOwner(31L, OWNER_ID)

        assertFailsWith<MediaNotFoundException> { service.attach(OWNER_ID, POST_ID, listOf(30L, 31L)) }
        verify(mediaRepository).attach(30L, OWNER_ID, POST_ID, 0)
    }

    private fun ready(id: Long, postId: Long? = null, position: Int? = null) = Media(
        ownerId = OWNER_ID,
        storageKey = "media/$OWNER_ID/$id.png",
        contentType = "image/png",
        sizeBytes = 10L,
        status = MediaStatus.READY,
        postId = postId,
        position = position,
        id = id,
    )

    private fun Media.copy(status: MediaStatus) = Media(
        ownerId = ownerId,
        storageKey = storageKey,
        contentType = contentType,
        sizeBytes = sizeBytes,
        status = status,
        id = id,
    )

    private companion object {
        const val OWNER_ID = 1L
        const val POST_ID = 10L
    }
}

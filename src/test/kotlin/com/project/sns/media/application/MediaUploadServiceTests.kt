package com.project.sns.media.application

import com.project.sns.media.domain.Media
import com.project.sns.media.domain.MediaInvalidException
import com.project.sns.media.domain.MediaNotFoundException
import com.project.sns.media.domain.MediaNotUploadedException
import com.project.sns.media.domain.MediaRepository
import com.project.sns.media.domain.MediaStatus
import com.project.sns.media.domain.MediaStorage
import com.project.sns.media.domain.MediaTooLargeException
import com.project.sns.media.domain.MediaTypeNotAllowedException
import com.project.sns.media.infrastructure.MediaProperties
import java.net.URL
import java.time.Duration
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MediaUploadServiceTests {
    private val mediaRepository = mock(MediaRepository::class.java)
    private val mediaStorage = mock(MediaStorage::class.java)
    private val mediaVerifier = mock(MediaVerifier::class.java)
    private val properties = MediaProperties(maxSizeBytes = 1_000L, uploadUrlTtl = Duration.ofMinutes(5))
    private val service = MediaUploadService(mediaRepository, mediaStorage, mediaVerifier, properties)

    @Test
    fun `발급은 허용 형식과 크기 상한 안에서만 행을 만들고 presigned PUT 을 돌려준다`() {
        doReturn(pending(id = MEDIA_ID)).`when`(mediaRepository).save(any(Media::class.java) ?: pending())
        doReturn(URL("https://s3.test/put")).`when`(mediaStorage)
            .presignUpload(anyString(), eq("image/png") ?: "", eq(500L), eq(Duration.ofMinutes(5)) ?: Duration.ZERO)

        val ticket = service.initiate(OWNER_ID, "image/png", 500L)

        assertEquals(MEDIA_ID, ticket.mediaId)
        assertEquals("https://s3.test/put", ticket.uploadUrl.toString())
        assertTrue(ticket.expiresAt.isAfter(java.time.Instant.now().minusSeconds(1)))
    }

    @Test
    fun `허용되지 않는 형식이나 상한 초과는 행을 만들지 않는다`() {
        assertFailsWith<MediaTypeNotAllowedException> { service.initiate(OWNER_ID, "image/svg+xml", 10L) }
        assertFailsWith<MediaTypeNotAllowedException> { service.initiate(OWNER_ID, "application/pdf", 10L) }
        assertFailsWith<MediaTooLargeException> { service.initiate(OWNER_ID, "image/png", 1_001L) }
        verifyNoInteractions(mediaRepository, mediaStorage)
    }

    @Test
    fun `Ready 판정이면 markReady 로 READY 가 된다`() {
        doReturn(pending(), ready()).`when`(mediaRepository).findActiveByIdAndOwner(MEDIA_ID, OWNER_ID)
        doReturn(MediaVerdict.Ready(2, 3)).`when`(mediaVerifier).verify(any(Media::class.java) ?: pending())
        doReturn(true).`when`(mediaRepository).markReady(MEDIA_ID, 2, 3)

        assertEquals(MediaStatus.READY, service.complete(OWNER_ID, MEDIA_ID).status)
        verify(mediaRepository).markReady(MEDIA_ID, 2, 3)
        verify(mediaStorage, never()).delete(anyString())
    }

    @Test
    fun `이미 READY 면 검증도 S3 도 부르지 않고 그대로 돌려준다`() {
        doReturn(ready()).`when`(mediaRepository).findActiveByIdAndOwner(MEDIA_ID, OWNER_ID)

        assertEquals(MediaStatus.READY, service.complete(OWNER_ID, MEDIA_ID).status)
        verifyNoInteractions(mediaVerifier, mediaStorage)
        verify(mediaRepository, never()).markReady(anyLong(), any(), any())
    }

    @Test
    fun `남의 미디어나 없는 미디어는 404 이고 검증하지 않는다`() {
        doReturn(null).`when`(mediaRepository).findActiveByIdAndOwner(MEDIA_ID, OWNER_ID)

        assertFailsWith<MediaNotFoundException> { service.complete(OWNER_ID, MEDIA_ID) }
        verifyNoInteractions(mediaVerifier, mediaStorage)
    }

    @Test
    fun `NotUploaded 판정이면 409 이고 행은 PENDING 으로 남는다`() {
        doReturn(pending()).`when`(mediaRepository).findActiveByIdAndOwner(MEDIA_ID, OWNER_ID)
        doReturn(MediaVerdict.NotUploaded).`when`(mediaVerifier).verify(any(Media::class.java) ?: pending())

        assertFailsWith<MediaNotUploadedException> { service.complete(OWNER_ID, MEDIA_ID) }
        verify(mediaRepository, never()).softDeletePending(MEDIA_ID)
        verify(mediaStorage, never()).delete(KEY)
    }

    @Test
    fun `Invalid 판정이면 행을 폐기한 뒤 객체를 지우고 400 이다`() {
        doReturn(pending()).`when`(mediaRepository).findActiveByIdAndOwner(MEDIA_ID, OWNER_ID)
        doReturn(MediaVerdict.Invalid("불일치")).`when`(mediaVerifier).verify(any(Media::class.java) ?: pending())
        doReturn(true).`when`(mediaRepository).softDeletePending(MEDIA_ID)

        assertFailsWith<MediaInvalidException> { service.complete(OWNER_ID, MEDIA_ID) }
        val order = inOrder(mediaRepository, mediaStorage)
        order.verify(mediaRepository).softDeletePending(MEDIA_ID)
        order.verify(mediaStorage).delete(KEY)
        verify(mediaRepository, never()).markReady(anyLong(), any(), any())
    }

    @Test
    fun `폐기하려는 사이 다른 완료가 READY 로 바꿨으면 객체를 건드리지 않고 그 결과를 돌려준다`() {
        doReturn(pending(), ready()).`when`(mediaRepository).findActiveByIdAndOwner(MEDIA_ID, OWNER_ID)
        doReturn(MediaVerdict.Invalid("불일치")).`when`(mediaVerifier).verify(any(Media::class.java) ?: pending())
        doReturn(false).`when`(mediaRepository).softDeletePending(MEDIA_ID)

        assertEquals(MediaStatus.READY, service.complete(OWNER_ID, MEDIA_ID).status)
        verify(mediaStorage, never()).delete(KEY)
    }

    private fun pending(id: Long = MEDIA_ID) =
        Media(ownerId = OWNER_ID, storageKey = KEY, contentType = "image/png", sizeBytes = 100L, id = id)

    private fun ready() = Media(
        ownerId = OWNER_ID,
        storageKey = KEY,
        contentType = "image/png",
        sizeBytes = 100L,
        status = MediaStatus.READY,
        id = MEDIA_ID
    )

    private companion object {
        const val OWNER_ID = 1L
        const val MEDIA_ID = 10L
        const val KEY = "media/1/test.png"
    }
}

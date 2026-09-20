package com.project.sns.media.application

import com.project.sns.media.TestImages
import com.project.sns.media.domain.Media
import com.project.sns.media.domain.MediaObjectMissingException
import com.project.sns.media.domain.MediaStorage
import com.project.sns.media.domain.StoredObject
import com.project.sns.media.infrastructure.MediaProperties
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MediaVerifierTests {
    private val mediaStorage = mock(MediaStorage::class.java)
    private val verifier = MediaVerifier(mediaStorage, MediaProperties(maxSizeBytes = 1_000L))

    @Test
    fun `선언과 같고 실제로 그 형식이면 Ready 에 픽셀 크기를 담는다`() {
        val bytes = TestImages.png(2, 3)
        doReturn(StoredObject("image/png", bytes.size.toLong())).`when`(mediaStorage).head(KEY)
        doReturn(bytes.inputStream()).`when`(mediaStorage).open(KEY)

        assertEquals(MediaVerdict.Ready(2, 3), verifier.verify(pending(bytes.size.toLong())))
    }

    @Test
    fun `객체가 없으면 NotUploaded 이고 GET 은 하지 않는다`() {
        doReturn(null).`when`(mediaStorage).head(KEY)

        assertEquals(MediaVerdict.NotUploaded, verifier.verify(pending()))
        verify(mediaStorage, never()).open(KEY)
    }

    @Test
    fun `HEAD 와 GET 사이에 객체가 사라지면 NotUploaded 다`() {
        doReturn(StoredObject("image/png", 100L)).`when`(mediaStorage).head(KEY)
        doThrow(MediaObjectMissingException(KEY)).`when`(mediaStorage).open(KEY)

        assertEquals(MediaVerdict.NotUploaded, verifier.verify(pending()))
    }

    @Test
    fun `HEAD 의 크기나 타입이 선언과 다르면 Invalid 이고 GET 은 하지 않는다`() {
        doReturn(StoredObject("image/png", 101L)).`when`(mediaStorage).head(KEY)
        assertIs<MediaVerdict.Invalid>(verifier.verify(pending(100L)))

        doReturn(StoredObject("image/jpeg", 100L)).`when`(mediaStorage).head(KEY)
        assertIs<MediaVerdict.Invalid>(verifier.verify(pending(100L)))

        doReturn(StoredObject("image/png", 1_001L)).`when`(mediaStorage).head(KEY)
        assertIs<MediaVerdict.Invalid>(verifier.verify(pending(1_001L)))

        verify(mediaStorage, never()).open(KEY)
    }

    @Test
    fun `선언은 png 인데 내용이 jpeg 이거나 이미지가 아니면 Invalid 다`() {
        val jpeg = TestImages.jpeg()
        doReturn(StoredObject("image/png", jpeg.size.toLong())).`when`(mediaStorage).head(KEY)
        doReturn(jpeg.inputStream()).`when`(mediaStorage).open(KEY)
        assertIs<MediaVerdict.Invalid>(verifier.verify(pending(jpeg.size.toLong())))

        val text = "not an image".toByteArray()
        doReturn(StoredObject("image/png", text.size.toLong())).`when`(mediaStorage).head(KEY)
        doReturn(text.inputStream()).`when`(mediaStorage).open(KEY)
        assertIs<MediaVerdict.Invalid>(verifier.verify(pending(text.size.toLong())))
    }

    @Test
    fun `webp 는 형식만 맞으면 Ready 이고 크기는 비운다`() {
        val webp = TestImages.webpHeaderOnly()
        doReturn(StoredObject("image/webp", webp.size.toLong())).`when`(mediaStorage).head(KEY)
        doReturn(webp.inputStream()).`when`(mediaStorage).open(KEY)

        assertEquals(
            MediaVerdict.Ready(null, null),
            verifier.verify(pending(webp.size.toLong(), contentType = "image/webp"))
        )
    }

    private fun pending(sizeBytes: Long = 100L, contentType: String = "image/png") = Media(
        ownerId = 1L,
        storageKey = KEY,
        contentType = contentType,
        sizeBytes = sizeBytes,
        id = 10L,
    )

    private companion object {
        const val KEY = "media/1/test.png"
    }
}

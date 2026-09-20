package com.project.sns.media.application

import com.project.sns.media.domain.Media
import com.project.sns.media.domain.MediaRepository
import com.project.sns.media.domain.MediaStatus
import com.project.sns.media.domain.MediaStorage
import com.project.sns.media.infrastructure.MediaProperties
import java.net.URL
import java.time.Duration
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaViewServiceTests {
    private val mediaRepository = mock(MediaRepository::class.java)
    private val mediaStorage = mock(MediaStorage::class.java)
    private val service =
        MediaViewService(mediaRepository, mediaStorage, MediaProperties(downloadUrlTtl = Duration.ofMinutes(15)))

    @Test
    fun `첨부 순서대로 설정된 TTL 의 읽기 URL 을 붙여 돌려준다`() {
        doReturn(listOf(attached(31L, 0), attached(30L, 1))).`when`(mediaRepository).findAttached(POST_ID)
        doReturn(URL("https://s3.test/get/31")).`when`(mediaStorage)
            .presignDownload("media/1/31.png", Duration.ofMinutes(15))
        doReturn(URL("https://s3.test/get/30")).`when`(mediaStorage)
            .presignDownload("media/1/30.png", Duration.ofMinutes(15))

        val result = service.listForPost(POST_ID)

        assertEquals(listOf(31L, 30L), result.map { it.id })
        assertEquals("https://s3.test/get/31", result[0].url.toString())
        verify(mediaStorage).presignDownload("media/1/30.png", Duration.ofMinutes(15))
    }

    @Test
    fun `첨부가 없으면 빈 목록이고 서명하지 않는다`() {
        doReturn(emptyList<Media>()).`when`(mediaRepository).findAttached(POST_ID)

        assertTrue(service.listForPost(POST_ID).isEmpty())
        verifyNoInteractions(mediaStorage)
    }

    private fun attached(id: Long, position: Int) = Media(
        ownerId = 1L,
        storageKey = "media/1/$id.png",
        contentType = "image/png",
        sizeBytes = 10L,
        status = MediaStatus.READY,
        postId = POST_ID,
        position = position,
        id = id,
    )

    private companion object {
        const val POST_ID = 10L
    }
}

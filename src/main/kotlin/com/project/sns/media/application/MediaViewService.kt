package com.project.sns.media.application

import com.project.sns.media.domain.Media
import com.project.sns.media.domain.MediaRepository
import com.project.sns.media.domain.MediaStorage
import com.project.sns.media.infrastructure.MediaProperties
import java.net.URL
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MediaViewService(
    private val mediaRepository: MediaRepository,
    private val mediaStorage: MediaStorage,
    private val properties: MediaProperties,
) {
    @Transactional(readOnly = true)
    fun listForPost(postId: Long): List<MediaDetail> = mediaRepository.findAttached(postId).map { it.toDetail() }

    private fun Media.toDetail() = MediaDetail(
        id = requireNotNull(id),
        contentType = contentType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        url = mediaStorage.presignDownload(storageKey, properties.downloadUrlTtl),
    )
}

data class MediaDetail(
    val id: Long,
    val contentType: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val url: URL,
)

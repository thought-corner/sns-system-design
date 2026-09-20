package com.project.sns.media.application

import com.project.sns.media.domain.Media
import com.project.sns.media.domain.MediaInvalidException
import com.project.sns.media.domain.MediaNotFoundException
import com.project.sns.media.domain.MediaNotUploadedException
import com.project.sns.media.domain.MediaPolicy
import com.project.sns.media.domain.MediaRepository
import com.project.sns.media.domain.MediaStatus
import com.project.sns.media.domain.MediaStorage
import com.project.sns.media.domain.MediaTooLargeException
import com.project.sns.media.domain.MediaTypeNotAllowedException
import com.project.sns.media.infrastructure.MediaProperties
import java.net.URL
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MediaUploadService(
    private val mediaRepository: MediaRepository,
    private val mediaStorage: MediaStorage,
    private val mediaVerifier: MediaVerifier,
    private val properties: MediaProperties,
) {
    fun initiate(ownerId: Long, contentType: String, sizeBytes: Long): UploadTicket {
        if (contentType !in MediaPolicy.ALLOWED_CONTENT_TYPES) throw MediaTypeNotAllowedException()
        if (sizeBytes > properties.maxSizeBytes) throw MediaTooLargeException()

        val key = "media/$ownerId/${UUID.randomUUID()}.${MediaPolicy.extensionOf(contentType)}"
        val media = mediaRepository.save(
            Media(ownerId = ownerId, storageKey = key, contentType = contentType, sizeBytes = sizeBytes),
        )
        val expiresAt = Instant.now().plus(properties.uploadUrlTtl)
        val url = mediaStorage.presignUpload(key, contentType, sizeBytes, properties.uploadUrlTtl)
        return UploadTicket(mediaId = requireNotNull(media.id), uploadUrl = url, expiresAt = expiresAt)
    }

    fun complete(ownerId: Long, mediaId: Long): Media {
        val media = mediaRepository.findActiveByIdAndOwner(mediaId, ownerId) ?: throw MediaNotFoundException()
        if (media.status == MediaStatus.READY) return media

        when (val verdict = mediaVerifier.verify(media)) {
            MediaVerdict.NotUploaded -> throw MediaNotUploadedException()
            is MediaVerdict.Invalid -> {
                if (mediaRepository.softDeletePending(mediaId)) {
                    logger.warn("미디어 검증 실패로 폐기: mediaId={} key={} {}", mediaId, media.storageKey, verdict.reason)
                    mediaStorage.delete(media.storageKey)
                    throw MediaInvalidException()
                }
                logger.info("미디어 폐기 건너뜀 — 동시 완료가 먼저 READY 로 바꿈: mediaId={}", mediaId)
            }

            is MediaVerdict.Ready -> {
                if (!mediaRepository.markReady(mediaId, verdict.width, verdict.height)) {
                    logger.info("미디어 완료 처리가 동시에 두 번 들어옴: mediaId={}", mediaId)
                }
            }
        }
        return mediaRepository.findActiveByIdAndOwner(mediaId, ownerId) ?: throw MediaNotFoundException()
    }

    private companion object {
        val logger = LoggerFactory.getLogger(MediaUploadService::class.java)
    }
}

data class UploadTicket(
    val mediaId: Long,
    val uploadUrl: URL,
    val expiresAt: Instant,
)

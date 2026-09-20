package com.project.sns.media.application

import com.project.sns.media.domain.ImageProbe
import com.project.sns.media.domain.Media
import com.project.sns.media.domain.MediaObjectMissingException
import com.project.sns.media.domain.MediaStorage
import com.project.sns.media.infrastructure.MediaProperties
import org.springframework.stereotype.Component

sealed interface MediaVerdict {
    data class Ready(val width: Int?, val height: Int?) : MediaVerdict

    data object NotUploaded : MediaVerdict

    data class Invalid(val reason: String) : MediaVerdict
}

@Component
class MediaVerifier(
    private val mediaStorage: MediaStorage,
    private val properties: MediaProperties,
) {
    fun verify(media: Media): MediaVerdict {
        val stored = mediaStorage.head(media.storageKey) ?: return MediaVerdict.NotUploaded
        if (stored.sizeBytes != media.sizeBytes || stored.sizeBytes > properties.maxSizeBytes ||
            !stored.contentType.equals(media.contentType, ignoreCase = true)
        ) {
            return MediaVerdict.Invalid("HEAD 불일치 contentType=${stored.contentType} size=${stored.sizeBytes}")
        }

        val image = try {
            mediaStorage.open(media.storageKey).use { ImageProbe.probe(it) }
        } catch (e: MediaObjectMissingException) {
            return MediaVerdict.NotUploaded
        }
        if (image == null || image.contentType != media.contentType) {
            return MediaVerdict.Invalid("내용 불일치 detected=${image?.contentType}")
        }
        return MediaVerdict.Ready(width = image.width, height = image.height)
    }
}

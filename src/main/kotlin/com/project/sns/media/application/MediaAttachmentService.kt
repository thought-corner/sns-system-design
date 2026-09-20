package com.project.sns.media.application

import com.project.sns.media.domain.MediaAlreadyAttachedException
import com.project.sns.media.domain.MediaNotFoundException
import com.project.sns.media.domain.MediaNotReadyException
import com.project.sns.media.domain.MediaPolicy
import com.project.sns.media.domain.MediaRepository
import com.project.sns.media.domain.MediaStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MediaAttachmentService(
    private val mediaRepository: MediaRepository,
) {
    @Transactional
    fun attach(ownerId: Long, postId: Long, mediaIds: List<Long>) {
        if (mediaIds.isEmpty()) return
        require(mediaIds.size <= MediaPolicy.MAX_PER_POST) { "미디어는 게시글당 ${MediaPolicy.MAX_PER_POST}개까지" }
        require(mediaIds.toSet().size == mediaIds.size) { "mediaIds 에 중복이 있다" }

        mediaIds.withIndex().sortedBy { it.value }.forEach { (position, mediaId) ->
            if (!mediaRepository.attach(mediaId, ownerId, postId, position)) {
                val media = mediaRepository.findActiveByIdAndOwner(mediaId, ownerId) ?: throw MediaNotFoundException()
                if (media.status != MediaStatus.READY) throw MediaNotReadyException()
                throw MediaAlreadyAttachedException()
            }
        }
    }
}

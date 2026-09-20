package com.project.sns.media.infrastructure

import com.project.sns.media.domain.Media
import com.project.sns.media.domain.MediaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class MediaRepositoryAdapter(
    private val jpaRepository: SpringDataMediaJpaRepository,
) : MediaRepository {
    override fun save(media: Media): Media = jpaRepository.save(media)

    override fun findActiveByIdAndOwner(id: Long, ownerId: Long): Media? =
        jpaRepository.findByIdAndOwnerIdAndDeletedAtIsNull(id, ownerId)

    override fun findAttached(postId: Long): List<Media> =
        jpaRepository.findByPostIdAndDeletedAtIsNullOrderByPositionAsc(postId)

    @Transactional
    override fun markReady(id: Long, width: Int?, height: Int?): Boolean =
        jpaRepository.markReady(id, width, height) == 1

    @Transactional
    override fun attach(id: Long, ownerId: Long, postId: Long, position: Int): Boolean =
        jpaRepository.attach(id, ownerId, postId, position) == 1

    @Transactional
    override fun softDeletePending(id: Long): Boolean = jpaRepository.softDeletePendingById(id) == 1
}

package com.project.sns.timeline.application

import com.project.sns.media.application.MediaDetail
import com.project.sns.media.application.MediaRef
import com.project.sns.media.application.MediaViewService
import com.project.sns.post.application.PostDetail
import com.project.sns.post.domain.PostCounts
import com.project.sns.post.domain.PostCountsRepository
import com.project.sns.post.domain.PostRepository
import com.project.sns.timeline.domain.MediaSnapshot
import com.project.sns.timeline.domain.PostCache
import com.project.sns.timeline.domain.PostSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class PostReadService(
    private val postCache: PostCache,
    private val postRepository: PostRepository,
    private val postCountsRepository: PostCountsRepository,
    private val mediaViewService: MediaViewService,
) {
    fun loadActive(ids: Collection<Long>): Map<Long, PostDetail> {
        if (ids.isEmpty()) return emptyMap()
        val active = postRepository.findActiveIds(ids)
        if (active.isEmpty()) return emptyMap()
        val cached = (safely("조회") { postCache.getAll(active) } ?: emptyMap()).filterKeys { it in active }
        val misses = active.filterNot { it in cached }
        val loaded = if (misses.isEmpty()) emptyList() else snapshotsFromDb(misses)
        if (loaded.isNotEmpty()) safely("저장") { postCache.putAll(loaded) }
        val snapshots = cached + loaded.associateBy { it.id }
        val counts = postCountsRepository.getAll(snapshots.keys)
        return snapshots.mapValues { (id, snapshot) -> snapshot.toDetail(counts[id] ?: PostCounts(postId = id)) }
    }

    fun evictAfterCommit(postId: Long) {
        val action = { safely("무효화") { postCache.evict(postId) } ?: Unit }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = action()
            },
        )
    }

    private fun snapshotsFromDb(ids: Collection<Long>): List<PostSnapshot> {
        val posts = postRepository.findActiveByIds(ids)
        if (posts.isEmpty()) return emptyList()
        val media = mediaViewService.listRefsForPosts(posts.map { requireNotNull(it.id) })
        return posts.map { post ->
            val id = requireNotNull(post.id)
            PostSnapshot(
                id = id,
                authorId = post.authorId,
                content = post.content,
                parentPostId = post.parentPostId,
                quotedPostId = post.quotedPostId,
                repostOfId = post.repostOfId,
                createdAt = post.createdAt,
                media = (media[id] ?: emptyList()).map { it.toSnapshot() },
            )
        }
    }

    private fun MediaRef.toSnapshot() = MediaSnapshot(
        id = id,
        contentType = contentType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        storageKey = storageKey,
    )

    private fun MediaSnapshot.toDetail(): MediaDetail = mediaViewService.sign(
        MediaRef(id = id, contentType = contentType, sizeBytes = sizeBytes, width = width, height = height, storageKey = storageKey),
    )

    private fun PostSnapshot.toDetail(counts: PostCounts) = PostDetail(
        id = id,
        authorId = authorId,
        content = content,
        parentPostId = parentPostId,
        quotedPostId = quotedPostId,
        repostOfId = repostOfId,
        createdAt = createdAt,
        counts = counts,
        media = media.map { it.toDetail() },
    )

    private fun <T> safely(what: String, action: () -> T): T? = try {
        action()
    } catch (e: RuntimeException) {
        logger.warn("게시글 캐시 {} 실패 — PostgreSQL 로 진행", what, e)
        null
    }

    private companion object {
        val logger = LoggerFactory.getLogger(PostReadService::class.java)
    }
}

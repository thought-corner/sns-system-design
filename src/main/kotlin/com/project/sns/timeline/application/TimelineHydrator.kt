package com.project.sns.timeline.application

import com.project.sns.post.application.PostDetail
import org.springframework.stereotype.Component

@Component
class TimelineHydrator(
    private val postReadService: PostReadService,
) {
    fun loadActive(ids: List<Long>): List<PostDetail> {
        if (ids.isEmpty()) return emptyList()
        val byId = postReadService.loadActive(ids)
        return ids.mapNotNull { byId[it] }
    }

    fun toItems(posts: List<PostDetail>): List<TimelineItem> {
        if (posts.isEmpty()) return emptyList()
        val own = posts.associateBy { it.id }
        val originalIds = posts.mapNotNull { it.repostOfId }.filterNot { it in own }.distinct()
        val originals = if (originalIds.isEmpty()) emptyMap() else postReadService.loadActive(originalIds)
        val all = own + originals
        return posts.mapNotNull { post ->
            val original = post.repostOfId?.let { all[it] ?: return@mapNotNull null }
            TimelineItem(post = post, original = original)
        }
    }
}

data class TimelineItem(
    val post: PostDetail,
    val original: PostDetail?,
)

package com.project.sns.timeline.presentation

import com.project.sns.auth.presentation.AuthenticatedPrincipal
import com.project.sns.auth.presentation.AuthenticatedUser
import com.project.sns.post.presentation.PostResponse
import com.project.sns.timeline.application.TimelinePage
import com.project.sns.timeline.application.TimelineService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/timeline")
class TimelineController(
    private val timelineService: TimelineService,
) {
    @GetMapping
    fun read(
        @AuthenticatedUser principal: AuthenticatedPrincipal,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): TimelineResponse = TimelineResponse.from(timelineService.read(principal.id, cursor, limit))
}

data class TimelineResponse(
    val items: List<TimelineItemResponse>,
    val nextCursor: Long?,
) {
    companion object {
        fun from(page: TimelinePage) = TimelineResponse(
            items = page.items.map {
                TimelineItemResponse(
                    post = PostResponse.from(it.post),
                    original = it.original?.let(PostResponse::from)
                )
            },
            nextCursor = page.nextCursor,
        )
    }
}

data class TimelineItemResponse(
    val post: PostResponse,
    val original: PostResponse?,
)

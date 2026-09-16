package com.project.sns.follow.presentation

import com.project.sns.auth.presentation.AuthenticatedPrincipal
import com.project.sns.auth.presentation.AuthenticatedUser
import com.project.sns.follow.application.FollowService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class FollowController(
    private val followService: FollowService,
) {
    @PutMapping("/{followingId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun follow(
        @AuthenticatedUser principal: AuthenticatedPrincipal,
        @PathVariable followingId: Long,
    ) {
        followService.follow(principal.id, followingId)
    }

    @DeleteMapping("/{followingId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unfollow(
        @AuthenticatedUser principal: AuthenticatedPrincipal,
        @PathVariable followingId: Long,
    ) {
        followService.unfollow(principal.id, followingId)
    }

    @GetMapping("/{userId}/follow-stats")
    fun getStats(@PathVariable userId: Long): FollowStatsResponse {
        val stats = followService.getStats(userId)
        return FollowStatsResponse(
            userId = userId,
            followerCount = stats.followerCount,
            followingCount = stats.followingCount,
        )
    }
}

data class FollowStatsResponse(
    val userId: Long,
    val followerCount: Long,
    val followingCount: Long,
)

package com.project.sns.post.presentation

import com.project.sns.auth.presentation.AuthenticatedPrincipal
import com.project.sns.auth.presentation.AuthenticatedUser
import com.project.sns.post.application.LikeService
import com.project.sns.post.application.RepostService
import com.project.sns.post.application.ViewService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/posts/{postId}")
class PostEngagementController(
    private val repostService: RepostService,
    private val likeService: LikeService,
    private val viewService: ViewService,
) {
    @PutMapping("/repost")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun repost(@AuthenticatedUser principal: AuthenticatedPrincipal, @PathVariable postId: Long) {
        repostService.repost(principal.id, postId)
    }

    @DeleteMapping("/repost")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun undoRepost(@AuthenticatedUser principal: AuthenticatedPrincipal, @PathVariable postId: Long) {
        repostService.undoRepost(principal.id, postId)
    }

    @PutMapping("/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun like(@AuthenticatedUser principal: AuthenticatedPrincipal, @PathVariable postId: Long) {
        likeService.like(principal.id, postId)
    }

    @DeleteMapping("/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlike(@AuthenticatedUser principal: AuthenticatedPrincipal, @PathVariable postId: Long) {
        likeService.unlike(principal.id, postId)
    }

    @PostMapping("/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun view(@AuthenticatedUser principal: AuthenticatedPrincipal, @PathVariable postId: Long) {
        viewService.view(principal.id, postId)
    }
}

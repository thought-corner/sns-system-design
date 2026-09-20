package com.project.sns.post.presentation

import com.project.sns.auth.presentation.AuthenticatedPrincipal
import com.project.sns.auth.presentation.AuthenticatedUser
import com.project.sns.post.application.PostService
import com.project.sns.post.application.QuoteService
import com.project.sns.post.application.ReplyService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/posts")
class PostController(
    private val postService: PostService,
    private val replyService: ReplyService,
    private val quoteService: QuoteService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticatedUser principal: AuthenticatedPrincipal,
        @Valid @RequestBody request: PostContentRequest,
    ): PostResponse = PostResponse.from(postService.create(principal.id, request.content))

    @GetMapping("/{postId}")
    fun get(@PathVariable postId: Long): PostResponse = PostResponse.from(postService.get(postId))

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticatedUser principal: AuthenticatedPrincipal,
        @PathVariable postId: Long,
    ) {
        postService.delete(principal.id, postId)
    }

    @PostMapping("/{postId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    fun reply(
        @AuthenticatedUser principal: AuthenticatedPrincipal,
        @PathVariable postId: Long,
        @Valid @RequestBody request: PostContentRequest,
    ): PostResponse = PostResponse.from(replyService.reply(principal.id, postId, request.content))

    @PostMapping("/{postId}/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    fun quote(
        @AuthenticatedUser principal: AuthenticatedPrincipal,
        @PathVariable postId: Long,
        @Valid @RequestBody request: PostContentRequest,
    ): PostResponse = PostResponse.from(quoteService.quote(principal.id, postId, request.content))
}

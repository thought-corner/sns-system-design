package com.project.sns.media.presentation

import com.project.sns.auth.presentation.AuthenticatedPrincipal
import com.project.sns.auth.presentation.AuthenticatedUser
import com.project.sns.media.application.MediaUploadService
import com.project.sns.media.application.UploadTicket
import com.project.sns.media.domain.Media
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/media")
class MediaController(
    private val mediaUploadService: MediaUploadService,
) {
    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    fun initiate(
        @AuthenticatedUser principal: AuthenticatedPrincipal,
        @Valid @RequestBody request: MediaUploadRequest,
    ): MediaUploadResponse = MediaUploadResponse.from(
        mediaUploadService.initiate(
            principal.id,
            requireNotNull(request.contentType),
            requireNotNull(request.sizeBytes)
        ),
    )

    @PostMapping("/{mediaId}/complete")
    fun complete(
        @AuthenticatedUser principal: AuthenticatedPrincipal,
        @PathVariable mediaId: Long,
    ): MediaResponse = MediaResponse.from(mediaUploadService.complete(principal.id, mediaId))
}

data class MediaUploadRequest(
    @field:NotBlank(message = "contentType 은 비어 있을 수 없습니다.")
    val contentType: String?,
    @field:NotNull(message = "sizeBytes 는 필수입니다.")
    @field:Positive(message = "sizeBytes 는 양수여야 합니다.")
    val sizeBytes: Long?,
)

data class MediaUploadResponse(
    val mediaId: Long,
    val uploadUrl: String,
    val expiresAt: Instant,
) {
    companion object {
        fun from(ticket: UploadTicket) = MediaUploadResponse(
            mediaId = ticket.mediaId,
            uploadUrl = ticket.uploadUrl.toString(),
            expiresAt = ticket.expiresAt,
        )
    }
}

data class MediaResponse(
    val id: Long,
    val status: String,
    val contentType: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
) {
    companion object {
        fun from(media: Media) = MediaResponse(
            id = requireNotNull(media.id),
            status = media.status.name,
            contentType = media.contentType,
            sizeBytes = media.sizeBytes,
            width = media.width,
            height = media.height,
        )
    }
}

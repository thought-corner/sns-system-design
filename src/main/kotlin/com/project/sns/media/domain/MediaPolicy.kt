package com.project.sns.media.domain

object MediaPolicy {
    const val MAX_PER_POST = 4

    val ALLOWED_CONTENT_TYPES: Set<String> = setOf("image/jpeg", "image/png", "image/gif", "image/webp")

    fun extensionOf(contentType: String): String = when (contentType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> throw IllegalArgumentException("허용되지 않는 Content-Type: $contentType")
    }
}

package com.project.sns.media.domain

import java.io.InputStream
import java.net.URL
import java.time.Duration

data class StoredObject(
    val contentType: String?,
    val sizeBytes: Long,
)

interface MediaStorage {
    fun presignUpload(key: String, contentType: String, sizeBytes: Long, ttl: Duration): URL

    fun presignDownload(key: String, ttl: Duration): URL

    fun head(key: String): StoredObject?

    fun open(key: String): InputStream

    fun delete(key: String)
}

class MediaObjectMissingException(key: String) : RuntimeException("S3 객체가 없습니다: $key")

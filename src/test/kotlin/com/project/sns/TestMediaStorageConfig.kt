package com.project.sns

import com.project.sns.media.domain.MediaObjectMissingException
import com.project.sns.media.domain.MediaStorage
import com.project.sns.media.domain.StoredObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration(proxyBeanMethods = false)
class TestMediaStorageConfig {
    @Bean
    fun mediaStorage(): InMemoryMediaStorage = InMemoryMediaStorage()
}

class InMemoryMediaStorage : MediaStorage {
    private val objects = ConcurrentHashMap<String, Pair<String, ByteArray>>()

    fun put(key: String, contentType: String, bytes: ByteArray) {
        objects[key] = contentType to bytes
    }

    fun contains(key: String): Boolean = objects.containsKey(key)

    fun clear() = objects.clear()

    override fun presignUpload(key: String, contentType: String, sizeBytes: Long, ttl: Duration): URL =
        URL("https://media.test/upload/$key?ct=$contentType&len=$sizeBytes")

    override fun presignDownload(key: String, ttl: Duration): URL = URL("https://media.test/download/$key")

    override fun head(key: String): StoredObject? = objects[key]?.let { (contentType, bytes) ->
        StoredObject(contentType = contentType, sizeBytes = bytes.size.toLong())
    }

    override fun open(key: String): InputStream =
        objects[key]?.let { ByteArrayInputStream(it.second) } ?: throw MediaObjectMissingException(key)

    override fun delete(key: String) {
        objects.remove(key)
    }
}

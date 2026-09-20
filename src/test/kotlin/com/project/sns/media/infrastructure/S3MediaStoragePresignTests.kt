package com.project.sns.media.infrastructure

import java.net.URLDecoder
import java.time.Duration
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class S3MediaStoragePresignTests {
    private val presigner = S3Presigner.builder()
        .region(Region.AP_NORTHEAST_2)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .build()
    private val storage = S3MediaStorage(mock(S3Client::class.java), presigner, "bucket")

    @Test
    fun `업로드 URL 은 content-type 과 content-length 를 서명 헤더에 묶는다`() {
        val url = storage.presignUpload("media/1/x.png", "image/png", 1234L, Duration.ofMinutes(10))

        val query = url.query.split("&")
            .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), Charsets.UTF_8) }
        val signedHeaders = query.getValue("X-Amz-SignedHeaders").split(";").toSet()
        assertTrue("content-type" in signedHeaders, "signed=$signedHeaders")
        assertTrue("content-length" in signedHeaders, "signed=$signedHeaders")
        assertEquals("600", query["X-Amz-Expires"])
        assertTrue(url.path.endsWith("/media/1/x.png"))
    }

    @Test
    fun `읽기 URL 은 host 만 서명하고 TTL 을 초로 담는다`() {
        val url = storage.presignDownload("media/1/x.png", Duration.ofMinutes(15))

        val query = url.query.split("&")
            .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), Charsets.UTF_8) }
        assertEquals("host", query["X-Amz-SignedHeaders"])
        assertEquals("900", query["X-Amz-Expires"])
    }
}

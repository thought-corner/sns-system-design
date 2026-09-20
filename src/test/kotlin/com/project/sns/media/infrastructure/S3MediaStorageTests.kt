package com.project.sns.media.infrastructure

import com.project.sns.media.domain.MediaObjectMissingException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class S3MediaStorageTests {
    private val s3 = mock(S3Client::class.java)
    private val storage = S3MediaStorage(s3, mock(S3Presigner::class.java), "bucket")

    @Test
    fun `HEAD 의 NoSuchKey 와 404 는 없음으로 본다`() {
        doThrow(NoSuchKeyException.builder().statusCode(404).build()).`when`(s3)
            .headObject(any(HeadObjectRequest::class.java))
        assertNull(storage.head("k"))

        doThrow(S3Exception.builder().statusCode(404).build()).`when`(s3).headObject(any(HeadObjectRequest::class.java))
        assertNull(storage.head("k"))
    }

    @Test
    fun `HEAD 403 은 설정 오류라 그대로 올린다`() {
        doThrow(S3Exception.builder().statusCode(403).build()).`when`(s3).headObject(any(HeadObjectRequest::class.java))

        assertFailsWith<S3Exception> { storage.head("k") }
    }

    @Test
    fun `GET 의 NoSuchKey 와 404 는 MediaObjectMissingException 이고 403 은 그대로 올린다`() {
        doThrow(NoSuchKeyException.builder().statusCode(404).build()).`when`(s3)
            .getObject(any(GetObjectRequest::class.java))
        assertFailsWith<MediaObjectMissingException> { storage.open("k") }

        doThrow(S3Exception.builder().statusCode(404).build()).`when`(s3).getObject(any(GetObjectRequest::class.java))
        assertFailsWith<MediaObjectMissingException> { storage.open("k") }

        doThrow(S3Exception.builder().statusCode(403).build()).`when`(s3).getObject(any(GetObjectRequest::class.java))
        assertFailsWith<S3Exception> { storage.open("k") }
    }
}

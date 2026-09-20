package com.project.sns.media.infrastructure

import com.project.sns.media.domain.MediaObjectMissingException
import com.project.sns.media.domain.MediaStorage
import com.project.sns.media.domain.StoredObject
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URL
import java.time.Duration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

class S3MediaStorage(
    private val s3: S3Client,
    private val presigner: S3Presigner,
    private val bucket: String,
) : MediaStorage {
    override fun presignUpload(key: String, contentType: String, sizeBytes: Long, ttl: Duration): URL {
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(sizeBytes)
            .build()
        return presigner.presignPutObject(
            PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(request).build(),
        ).url()
    }

    override fun presignDownload(key: String, ttl: Duration): URL {
        val request = GetObjectRequest.builder().bucket(bucket).key(key).build()
        return presigner.presignGetObject(
            GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(request).build(),
        ).url()
    }

    override fun head(key: String): StoredObject? = try {
        val response = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
        StoredObject(contentType = response.contentType(), sizeBytes = response.contentLength())
    } catch (e: NoSuchKeyException) {
        null
    } catch (e: S3Exception) {
        if (e.statusCode() == 404) null else throw e
    }

    override fun open(key: String): InputStream = try {
        val response = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())
        object : FilterInputStream(response) {
            override fun close() = response.abort()
        }
    } catch (e: NoSuchKeyException) {
        throw MediaObjectMissingException(key)
    } catch (e: S3Exception) {
        if (e.statusCode() == 404) throw MediaObjectMissingException(key) else throw e
    }

    override fun delete(key: String) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
    }
}

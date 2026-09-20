package com.project.sns.media.infrastructure

import com.project.sns.media.domain.MediaStorage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
@Profile("!test")
class S3MediaStorageConfig {
    @Bean
    fun s3Client(properties: MediaProperties): S3Client =
        S3Client.builder().region(Region.of(properties.s3.region)).build()

    @Bean
    fun s3Presigner(properties: MediaProperties): S3Presigner =
        S3Presigner.builder().region(Region.of(properties.s3.region)).build()

    @Bean
    fun mediaStorage(s3Client: S3Client, s3Presigner: S3Presigner, properties: MediaProperties): MediaStorage =
        S3MediaStorage(s3Client, s3Presigner, properties.s3.bucket)
}

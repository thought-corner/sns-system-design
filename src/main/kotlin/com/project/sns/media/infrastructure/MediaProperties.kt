package com.project.sns.media.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableConfigurationProperties(MediaProperties::class)
class MediaPropertiesConfig

@ConfigurationProperties("sns.media")
data class MediaProperties(
    val s3: S3 = S3(),
    val uploadUrlTtl: Duration = Duration.ofMinutes(10),
    val downloadUrlTtl: Duration = Duration.ofMinutes(15),
    val maxSizeBytes: Long = 10L * 1024 * 1024,
) {
    data class S3(
        val bucket: String = "sns-media",
        val region: String = "ap-northeast-2",
    )
}

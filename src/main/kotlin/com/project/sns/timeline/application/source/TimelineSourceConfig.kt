package com.project.sns.timeline.application.source

import com.project.sns.timeline.application.TimelineFanoutService
import com.project.sns.timeline.domain.PostCandidateRepository
import com.project.sns.timeline.domain.TimelinePolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TimelineSourceConfig {
    @Bean
    fun popularCandidateSource(
        postCandidateRepository: PostCandidateRepository,
        fanoutService: TimelineFanoutService,
    ): PopularCandidateSource? = if (TimelinePolicy.POPULAR_SOURCE_ENABLED) PopularCandidateSource(
        postCandidateRepository,
        fanoutService
    ) else null
}

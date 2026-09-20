package com.project.sns.timeline.infrastructure

import com.project.sns.timeline.domain.FanoutQueue
import com.project.sns.timeline.domain.FollowCache
import com.project.sns.timeline.domain.PostCache
import com.project.sns.timeline.domain.TimelineStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import tools.jackson.databind.ObjectMapper

@Configuration
@Profile("!test")
class RedisTimelineConfig {
    @Bean
    fun timelineStore(redisTemplate: StringRedisTemplate): TimelineStore = RedisTimelineStore(redisTemplate)

    @Bean
    fun fanoutQueue(redisTemplate: StringRedisTemplate): FanoutQueue =
        RedisFanoutQueue(redisTemplate).also { it.ensureGroup() }

    @Bean
    fun followCache(redisTemplate: StringRedisTemplate): FollowCache = RedisFollowCache(redisTemplate)

    @Bean
    fun postCache(redisTemplate: StringRedisTemplate, objectMapper: ObjectMapper): PostCache =
        RedisPostCache(redisTemplate, objectMapper)
}

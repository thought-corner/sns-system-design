package com.project.sns.auth.infrastructure

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.session.FlushMode
import org.springframework.session.SaveMode
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession

private const val SESSION_TIMEOUT_SECONDS = 30 * 60
private const val SESSION_NAMESPACE = "sns:session"

@Configuration
@Profile("!test")
@EnableRedisIndexedHttpSession(
    maxInactiveIntervalInSeconds = SESSION_TIMEOUT_SECONDS,
    redisNamespace = SESSION_NAMESPACE,
    flushMode = FlushMode.ON_SAVE,
    saveMode = SaveMode.ON_SET_ATTRIBUTE,
)
class RedisSessionConfig

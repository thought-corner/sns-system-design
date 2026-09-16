package com.project.sns

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

/**
 * 테스트 DB 는 운영과 같은 PostgreSQL 17 이다 — H2 는 부분 인덱스와 `ON CONFLICT (…) WHERE …` 를 지원하지 않아 쓰지 않는다.
 * 컨테이너는 JVM 당 한 번만 기동해 모든 Spring 컨텍스트가 공유한다. 종료는 Ryuk 이 맡되, Ryuk 을 끈 환경(`TESTCONTAINERS_RYUK_DISABLED`)에서도
 * 컨테이너가 남지 않도록 JVM 종료 훅에서 `stop()` 한다.
 * 테스트 클래스에는 직접 붙이지 말고 `@PostgresTest`(초기화기 + Docker 없으면 skip 을 한 짝으로) 를 쓴다.
 */
class PostgresContainerInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TestPropertyValues.of(
            "spring.datasource.url=${postgres.jdbcUrl}",
            "spring.datasource.username=$USERNAME",
            "spring.datasource.password=$PASSWORD",
            "spring.datasource.driver-class-name=org.postgresql.Driver",
        ).applyTo(applicationContext.environment)
    }

    private class PostgreSqlContainer : GenericContainer<PostgreSqlContainer>("postgres:17-alpine") {
        val jdbcUrl: String
            get() = "jdbc:postgresql://$host:${getMappedPort(POSTGRES_PORT)}/$DATABASE"
    }

    private companion object {
        const val POSTGRES_PORT = 5432
        const val DATABASE = "sns_test"
        const val USERNAME = "sns"
        const val PASSWORD = "sns"

        // 초기화 중 임시 서버가 한 번 더 뜨므로 "ready" 로그를 두 번 기다린다(공식 PostgreSQL 모듈과 같은 기준).
        val postgres: PostgreSqlContainer by lazy {
            PostgreSqlContainer()
                .withEnv("POSTGRES_DB", DATABASE)
                .withEnv("POSTGRES_USER", USERNAME)
                .withEnv("POSTGRES_PASSWORD", PASSWORD)
                .withExposedPorts(POSTGRES_PORT)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
                .also {
                    it.start()
                    Runtime.getRuntime().addShutdownHook(Thread(it::stop))
                }
        }
    }
}

package com.project.sns

import java.lang.annotation.Inherited
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * DB 가 필요한 테스트(`@SpringBootTest`·`@DataJpaTest`)에 붙인다.
 * 공유 PostgreSQL 컨테이너 접속 정보 주입(`PostgresContainerInitializer`)과 "Docker 없으면 skip" 을 한 짝으로 묶어 둘 중 하나를 빠뜨려 Docker 없는 환경에서 skip 대신 컨텍스트 로드 실패가 나는 일을 막는다.
 * 이 어노테이션 없이는 datasource 자체가 없어 어느 환경에서도 기동하지 않으므로 누락이 조용히 지나가지 않는다.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@ContextConfiguration(initializers = [PostgresContainerInitializer::class])
@Testcontainers(disabledWithoutDocker = true)
annotation class PostgresTest

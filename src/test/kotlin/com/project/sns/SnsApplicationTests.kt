package com.project.sns

import jakarta.persistence.EntityManagerFactory
import javax.sql.DataSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestSessionConfig::class)
@PostgresTest
class SnsApplicationTests {
    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun contextLoads() {
        assertNotNull(dataSource.connection.use { it.metaData })
        assertTrue(entityManagerFactory.isOpen)
    }

}

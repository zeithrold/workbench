package ink.doa.workbench.web.api

import ink.doa.workbench.core.port.locking.DistributedLockService
import ink.doa.workbench.core.storage.BlobStorage
import ink.doa.workbench.data.storage.blob.InMemoryBlobStorage
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.registerWorkbenchDataSource
import io.mockk.mockk
import java.time.Duration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.MOCK,
  properties =
    [
      "spring.profiles.active=integration,web",
      "management.health.redis.enabled=false",
    ],
)
@AutoConfigureMockMvc
@Import(InfrastructureEndpointIntegrationTest.TestBeans::class)
@Tag("integration")
class InfrastructureEndpointIntegrationTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `infrastructure endpoints are served under api prefix without authentication`() {
    mockMvc.perform(get("/api/actuator/health")).andExpect(status().isOk())
    mockMvc.perform(get("/api/openapi")).andExpect(status().isOk())
    mockMvc.perform(get("/api/scalar")).andExpect(status().is2xxSuccessful())
  }

  @Test
  fun `legacy infrastructure paths are not publicly exposed`() {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized())
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized())
    mockMvc.perform(get("/scalar")).andExpect(status().isUnauthorized())
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    @Primary
    fun noOpDistributedLockService(): DistributedLockService =
      object : DistributedLockService {
        override fun <T> withLock(
          name: String,
          wait: Duration,
          lease: Duration,
          block: () -> T,
        ): T = block()
      }

    @Bean @Primary fun redissonClient(): RedissonClient = mockk(relaxed = true)

    @Bean @Primary fun testBlobStorage(): BlobStorage = InMemoryBlobStorage()
  }

  companion object {
    private val postgresLease = WorkbenchPostgresTestSupport.moduleDatabase(MigrationSpec.Full)

    @JvmStatic
    @DynamicPropertySource
    fun registerProperties(registry: DynamicPropertyRegistry) {
      registry.registerWorkbenchDataSource(postgresLease)
    }
  }
}

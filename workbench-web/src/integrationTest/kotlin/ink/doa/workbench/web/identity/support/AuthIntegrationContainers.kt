package ink.doa.workbench.web.identity.support

import dasniko.testcontainers.keycloak.KeycloakContainer
import ink.doa.workbench.core.identity.auth.SecretResolver
import ink.doa.workbench.core.port.locking.DistributedLockService
import ink.doa.workbench.core.storage.BlobStorage
import ink.doa.workbench.data.storage.blob.InMemoryBlobStorage
import ink.doa.workbench.security.identity.auth.support.AuthIntegrationFixtures
import ink.doa.workbench.security.identity.auth.support.InMemoryLdapTestServer
import ink.doa.workbench.security.identity.auth.support.KeycloakTestContainer
import ink.doa.workbench.security.identity.auth.support.MapSecretResolver
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.registerWorkbenchDataSource
import io.mockk.mockk
import java.time.Duration
import org.redisson.api.RedissonClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry

object AuthIntegrationContainers {
  private val postgresLease = WorkbenchPostgresTestSupport.moduleDatabase(MigrationSpec.Full)

  val keycloak: KeycloakContainer = KeycloakTestContainer.shared()

  val ldap: InMemoryLdapTestServer = InMemoryLdapTestServer.start()

  fun registerDataSourceProperties(registry: DynamicPropertyRegistry) {
    registry.registerWorkbenchDataSource(postgresLease)
  }

  @TestConfiguration
  class AuthIntegrationTestConfiguration {
    @Bean
    @Primary
    fun testSecretResolver(): SecretResolver =
      MapSecretResolver(AuthIntegrationFixtures.keycloakSecrets())

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
}

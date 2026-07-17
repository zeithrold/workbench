package one.ztd.workbench.web.identity.support

import dasniko.testcontainers.keycloak.KeycloakContainer
import io.mockk.mockk
import java.time.Duration
import one.ztd.workbench.data.storage.blob.InMemoryBlobStorage
import one.ztd.workbench.identity.auth.SecretResolver
import one.ztd.workbench.kernel.port.locking.DistributedLockService
import one.ztd.workbench.kernel.storage.BlobStorage
import one.ztd.workbench.security.identity.auth.support.AuthIntegrationFixtures
import one.ztd.workbench.security.identity.auth.support.InMemoryLdapTestServer
import one.ztd.workbench.security.identity.auth.support.KeycloakTestContainer
import one.ztd.workbench.security.identity.auth.support.MapSecretResolver
import one.ztd.workbench.testsupport.postgres.MigrationSpec
import one.ztd.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import one.ztd.workbench.testsupport.postgres.registerWorkbenchDataSource
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

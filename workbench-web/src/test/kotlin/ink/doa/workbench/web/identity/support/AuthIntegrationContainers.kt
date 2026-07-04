package ink.doa.workbench.web.identity.support

import dasniko.testcontainers.keycloak.KeycloakContainer
import ink.doa.workbench.core.identity.auth.SecretResolver
import ink.doa.workbench.core.port.locking.DistributedLockService
import ink.doa.workbench.security.identity.auth.support.AuthIntegrationFixtures
import ink.doa.workbench.security.identity.auth.support.KeycloakTestContainer
import ink.doa.workbench.security.identity.auth.support.LdapTestContainer
import ink.doa.workbench.security.identity.auth.support.MapSecretResolver
import io.mockk.mockk
import java.time.Duration
import org.flywaydb.core.Flyway
import org.redisson.api.RedissonClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer

object AuthIntegrationContainers {
  val postgres: PostgreSQLContainer<*> =
    PostgreSQLContainer("postgres:18-alpine").apply {
      start()
      Flyway.configure()
        .dataSource(jdbcUrl, username, password)
        .locations(
          "classpath:db/migration",
          "classpath:ink/doa/workbench/data/migration",
        )
        .load()
        .migrate()
    }

  val keycloak: KeycloakContainer =
    KeycloakTestContainer.create().apply {
      withStartupTimeout(Duration.ofMinutes(3))
      start()
    }

  val ldap: GenericContainer<*> =
    LdapTestContainer.create().apply { LdapTestContainer.startAndBootstrap(this) }

  fun registerDataSourceProperties(registry: DynamicPropertyRegistry) {
    registry.add("spring.datasource.url") { postgres.jdbcUrl }
    registry.add("spring.datasource.username") { postgres.username }
    registry.add("spring.datasource.password") { postgres.password }
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
  }
}

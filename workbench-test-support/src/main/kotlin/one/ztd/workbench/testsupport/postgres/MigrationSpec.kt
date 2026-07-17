package one.ztd.workbench.testsupport.postgres

import org.flywaydb.core.api.configuration.FluentConfiguration

sealed interface MigrationSpec {
  fun locations(): Array<String>

  fun supportsTemplate(): Boolean

  fun templateKey(): String

  open fun configureFlyway(flyway: FluentConfiguration): FluentConfiguration = flyway

  data object Core : MigrationSpec {
    override fun locations(): Array<String> = arrayOf("classpath:db/migration")

    override fun supportsTemplate(): Boolean = true

    override fun templateKey(): String = "core"
  }

  data object Full : MigrationSpec {
    override fun locations(): Array<String> = arrayOf("classpath:db/migration")

    override fun supportsTemplate(): Boolean = true

    override fun templateKey(): String = "full"
  }

  class Custom
  internal constructor(
    private val migrationLocations: Array<String>,
    private val configure: FluentConfiguration.() -> FluentConfiguration,
    internal val cacheKey: String,
    internal val migrateOnOpen: Boolean,
  ) : MigrationSpec {
    override fun locations(): Array<String> = migrationLocations

    override fun supportsTemplate(): Boolean = false

    override fun templateKey(): String = error("Custom migration specs do not use templates")

    override fun configureFlyway(flyway: FluentConfiguration): FluentConfiguration =
      flyway.configure()

    companion object {
      fun create(
        locations: Array<String> = Full.locations(),
        migrateOnOpen: Boolean = true,
        configure: FluentConfiguration.() -> FluentConfiguration = { this },
      ): Custom {
        val cacheKey =
          "custom:${locations.joinToString(",")}:migrateOnOpen=$migrateOnOpen:${configure.hashCode()}"
        return Custom(locations, configure, cacheKey, migrateOnOpen)
      }
    }
  }
}

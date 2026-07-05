package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.WorkItemQueryFieldType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
class JdbcPostgresWorkItemFieldResolverIntegrationTest :
  StringSpec({
    "resolves property fields from jdbc metadata" {
      withPostgresJdbc { jdbc, tenantId, propertyApiId ->
        val resolver = JdbcPostgresWorkItemFieldResolver(jdbc, tenantId)
        val byCode =
          resolver.resolvePostgresField(QueryField.Property(apiId = null, code = "storyPoints"))
            as PostgresWorkItemField.Property
        byCode.definition.type shouldBe WorkItemQueryFieldType.NUMBER

        val byApiId =
          resolver.resolvePostgresField(
            QueryField.Property(apiId = propertyApiId, code = "storyPoints")
          ) as PostgresWorkItemField.Property
        byApiId.definition.type shouldBe WorkItemQueryFieldType.NUMBER
      }
    }

    "rejects unknown property definitions" {
      withPostgresJdbc { jdbc, tenantId, _ ->
        val resolver = JdbcPostgresWorkItemFieldResolver(jdbc, tenantId)

        shouldThrow<InvalidRequestException> {
          resolver.resolvePostgresField(QueryField.Property(apiId = null, code = "missing"))
        }
      }
    }
  })

private fun withPostgresJdbc(block: (JdbcTemplate, UUID, String) -> Unit) {
  PostgreSQLContainer("postgres:18-alpine").use { postgres ->
    postgres.start()
    Flyway.configure()
      .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
      .locations("classpath:db/migration")
      .load()
      .migrate()
    val dataSource =
      DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password).apply {
        setDriverClassName("org.postgresql.Driver")
      }
    val jdbc = JdbcTemplate(dataSource)
    val tenantId = UUID.randomUUID()
    val propertyId = UUID.randomUUID()
    val propertyApiId = "fld_${propertyId.toString().replace("-", "").take(12)}"
    jdbc.update(
      "INSERT INTO tenants (id, api_id, name, slug) VALUES (?, ?, ?, ?)",
      tenantId,
      "ten_${tenantId.toString().replace("-", "").take(12)}",
      "Tenant",
      "tenant",
    )
    jdbc.update(
      """
      INSERT INTO property_definitions (id, api_id, tenant_id, code, name, data_type, is_active)
      VALUES (?, ?, ?, ?, ?, ?, true)
      """
        .trimIndent(),
      propertyId,
      propertyApiId,
      tenantId,
      "storyPoints",
      "Story Points",
      "number",
    )
    block(jdbc, tenantId, propertyApiId)
  }
}

package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.workitem.WorkItemSearchScope
import ink.doa.workbench.core.workitem.query.ConditionNode
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.QueryOperator
import ink.doa.workbench.core.workitem.query.QueryValue
import ink.doa.workbench.core.workitem.query.WorkItemQuery
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
class JdbcWorkItemQueryRepositoryIntegrationTest :
  StringSpec({
    "search filters work items and maps PostgreSQL rows to common result container" {
      withPostgresJdbc { jdbc ->
        val seed = seedWorkItem(jdbc)
        val repository = jdbcWorkItemQueryRepository(jdbc)

        val page = runBlocking {
          repository.search(
            scope = WorkItemSearchScope(tenantId = seed.tenantId, projectId = seed.projectId),
            query =
              WorkItemQuery(
                where =
                  ConditionNode.And(
                    listOf(
                      ConditionNode.Predicate(
                        field = QueryField.System("statusGroup"),
                        op = QueryOperator.EQ,
                        value = QueryValue.Literal(JsonPrimitive("todo")),
                      ),
                      ConditionNode.Predicate(
                        field = QueryField.Property(apiId = null, code = "storyPoints"),
                        op = QueryOperator.GT,
                        value = QueryValue.Literal(JsonPrimitive(5)),
                      ),
                    )
                  )
              ),
          )
        }

        page.result.total shouldBe 1
        page.result.hits.single().apiId shouldBe seed.issueApiId
        page.result.hits.single().key shouldBe "CORE-1"
        page.result.hits.single().statusGroup shouldBe "todo"
        page.result.hits.single().properties["storyPoints"] shouldBe JsonPrimitive(8)
      }
    }
  })

private data class SeededWorkItem(
  val tenantId: UUID,
  val projectId: UUID,
  val issueApiId: String,
)

private fun withPostgresJdbc(block: (JdbcTemplate) -> Unit) {
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
    block(JdbcTemplate(dataSource))
  }
}

private fun seedWorkItem(jdbc: JdbcTemplate): SeededWorkItem {
  val tenantId = UUID.randomUUID()
  val userId = UUID.randomUUID()
  val projectId = UUID.randomUUID()
  val priorityId = UUID.randomUUID()
  val statusId = UUID.randomUUID()
  val workflowId = UUID.randomUUID()
  val issueTypeId = UUID.randomUUID()
  val issueTypeConfigId = UUID.randomUUID()
  val propertyId = UUID.randomUUID()
  val issueId = UUID.randomUUID()
  val issueApiId = "iss_${issueId.toString().replace("-", "").take(12)}"
  jdbc.update(
    "INSERT INTO tenants (id, api_id, name, slug) VALUES (?, ?, ?, ?)",
    tenantId,
    "ten_${tenantId.toString().replace("-", "").take(12)}",
    "Tenant",
    "tenant-${tenantId.toString().take(8)}",
  )
  jdbc.update(
    "INSERT INTO users (id, api_id, display_name) VALUES (?, ?, ?)",
    userId,
    "usr_${userId.toString().replace("-", "").take(12)}",
    "Ada",
  )
  jdbc.update(
    "INSERT INTO projects (id, api_id, tenant_id, name, identifier) VALUES (?, ?, ?, ?, ?)",
    projectId,
    "prj_${projectId.toString().replace("-", "").take(12)}",
    tenantId,
    "Core",
    "CORE",
  )
  jdbc.update(
    "INSERT INTO priorities (id, api_id, tenant_id, code, name, rank) VALUES (?, ?, ?, ?, ?, ?)",
    priorityId,
    "pri_${priorityId.toString().replace("-", "").take(12)}",
    tenantId,
    "high",
    "High",
    1,
  )
  jdbc.update(
    """
    INSERT INTO issue_statuses (id, api_id, tenant_id, code, name, status_group)
    VALUES (?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    statusId,
    "sts_${statusId.toString().replace("-", "").take(12)}",
    tenantId,
    "todo",
    "Todo",
    "todo",
  )
  jdbc.update(
    "INSERT INTO workflows (id, api_id, tenant_id, code, name) VALUES (?, ?, ?, ?, ?)",
    workflowId,
    "wfl_${workflowId.toString().replace("-", "").take(12)}",
    tenantId,
    "default",
    "Default",
  )
  jdbc.update(
    """
    INSERT INTO issue_types (id, api_id, tenant_id, scope, code, name)
    VALUES (?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    issueTypeId,
    "typ_${issueTypeId.toString().replace("-", "").take(12)}",
    tenantId,
    "tenant",
    "task",
    "Task",
  )
  jdbc.update(
    """
    INSERT INTO issue_type_configs (id, api_id, tenant_id, scope, issue_type_id, workflow_id)
    VALUES (?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    issueTypeConfigId,
    "itc_${issueTypeConfigId.toString().replace("-", "").take(12)}",
    tenantId,
    "tenant",
    issueTypeId,
    workflowId,
  )
  jdbc.update(
    """
    INSERT INTO property_definitions (id, api_id, tenant_id, code, name, data_type)
    VALUES (?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    propertyId,
    "fld_${propertyId.toString().replace("-", "").take(12)}",
    tenantId,
    "storyPoints",
    "Story Points",
    "number",
  )
  jdbc.update(
    """
    INSERT INTO issues (
      id, api_id, tenant_id, project_id, issue_type_id, issue_type_config_id,
      sequence_no, title, status_id, priority_id, reporter_id, created_by,
      properties_snapshot
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
    """
      .trimIndent(),
    issueId,
    issueApiId,
    tenantId,
    projectId,
    issueTypeId,
    issueTypeConfigId,
    1L,
    "First issue",
    statusId,
    priorityId,
    userId,
    userId,
    """{"storyPoints":8}""",
  )
  jdbc.update(
    """
    INSERT INTO issue_key_aliases (id, tenant_id, project_id, issue_id, issue_key, project_identifier, sequence_no)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    UUID.randomUUID(),
    tenantId,
    projectId,
    issueId,
    "CORE-1",
    "CORE",
    1L,
  )
  jdbc.update(
    """
    INSERT INTO issue_property_values (id, tenant_id, issue_id, property_id, value_number)
    VALUES (?, ?, ?, ?, ?)
    """
      .trimIndent(),
    UUID.randomUUID(),
    tenantId,
    issueId,
    propertyId,
    8,
  )
  return SeededWorkItem(tenantId = tenantId, projectId = projectId, issueApiId = issueApiId)
}

@Suppress("InjectDispatcher")
private fun jdbcWorkItemQueryRepository(jdbc: JdbcTemplate) =
  JdbcWorkItemQueryRepository(jdbc, Dispatchers.IO)

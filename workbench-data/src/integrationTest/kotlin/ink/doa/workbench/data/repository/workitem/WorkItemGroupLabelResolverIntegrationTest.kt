package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.workitem.query.ConditionNode
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.QueryOperator
import ink.doa.workbench.core.workitem.query.QueryValue
import ink.doa.workbench.core.workitem.query.WorkItemGroupLabel
import ink.doa.workbench.core.workitem.query.WorkItemGroupLabelCode
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.UUID
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.jdbc.core.JdbcTemplate

class WorkItemGroupLabelResolverIntegrationTest :
  StringSpec({
    "resolve returns text label for system statusGroup bucket" {
      withPostgresJdbc { jdbc ->
        val seed = seedStatusGroupLookup(jdbc, statusGroup = "todo")
        val resolver = WorkItemGroupLabelResolver(jdbc)
        val groupKey =
          ConditionNode.Predicate(
            field = QueryField.System("statusGroup"),
            op = QueryOperator.EQ,
            value = QueryValue.Literal(JsonPrimitive("todo")),
          )

        resolver.resolve(seed.tenantId, groupKey) shouldBe WorkItemGroupLabel.Text("Todo")
      }
    }

    "resolve returns message label for empty assignee bucket" {
      withPostgresJdbc { jdbc ->
        val seed = seedStatusGroupLookup(jdbc, statusGroup = "todo")
        val resolver = WorkItemGroupLabelResolver(jdbc)
        val groupKey =
          ConditionNode.Predicate(
            field = QueryField.System("assignee"),
            op = QueryOperator.IS_EMPTY,
          )

        resolver.resolve(seed.tenantId, groupKey) shouldBe
          WorkItemGroupLabelCode.EMPTY_ASSIGNEE.toLabel()
      }
    }

    "resolve returns text label for property single_select option" {
      withPostgresJdbc { jdbc ->
        val seed = seedSingleSelectProperty(jdbc, optionLabel = "Critical")
        val resolver = WorkItemGroupLabelResolver(jdbc)
        val groupKey =
          ConditionNode.Predicate(
            field = QueryField.Property(apiId = null, code = "severity"),
            op = QueryOperator.EQ,
            value = QueryValue.Literal(JsonPrimitive(seed.optionApiId)),
          )

        resolver.resolve(seed.tenantId, groupKey) shouldBe WorkItemGroupLabel.Text("Critical")
      }
    }

    "resolve returns message label for empty property single_select bucket" {
      withPostgresJdbc { jdbc ->
        val seed = seedSingleSelectProperty(jdbc, optionLabel = "Critical")
        val resolver = WorkItemGroupLabelResolver(jdbc)
        val groupKey =
          ConditionNode.Predicate(
            field = QueryField.Property(apiId = null, code = "severity"),
            op = QueryOperator.IS_EMPTY,
          )

        val label = resolver.resolve(seed.tenantId, groupKey)
        label.shouldBeInstanceOf<WorkItemGroupLabel.Message>()
        label.code shouldBe WorkItemGroupLabelCode.EMPTY_PROPERTY_OPTION.code
        label.args["propertyName"] shouldBe "Severity"
        label.defaultMessage shouldBe WorkItemGroupLabelCode.EMPTY_PROPERTY_OPTION.defaultMessage
      }
    }
  })

private data class StatusGroupLookupSeed(val tenantId: UUID)

private data class SingleSelectPropertySeed(
  val tenantId: UUID,
  val optionApiId: String,
)

private fun withPostgresJdbc(block: (JdbcTemplate) -> Unit) {
  WorkbenchPostgresTestSupport.withJdbcTemplate(MigrationSpec.Core, block)
}

private fun seedStatusGroupLookup(jdbc: JdbcTemplate, statusGroup: String): StatusGroupLookupSeed {
  val tenantId = UUID.randomUUID()
  jdbc.update(
    "INSERT INTO tenants (id, api_id, name, slug) VALUES (?, ?, ?, ?)",
    tenantId,
    "ten_${tenantId.toString().replace("-", "").take(12)}",
    "Tenant",
    "tenant-${tenantId.toString().take(8)}",
  )
  jdbc.update(
    """
    INSERT INTO issue_statuses (id, api_id, tenant_id, code, name, status_group)
    VALUES (?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    UUID.randomUUID(),
    "sts_${UUID.randomUUID().toString().replace("-", "").take(12)}",
    tenantId,
    statusGroup,
    statusGroup.replaceFirstChar { it.uppercase() },
    statusGroup,
  )
  return StatusGroupLookupSeed(tenantId = tenantId)
}

private fun seedSingleSelectProperty(
  jdbc: JdbcTemplate,
  optionLabel: String,
): SingleSelectPropertySeed {
  val tenantId = UUID.randomUUID()
  val propertyId = UUID.randomUUID()
  val optionId = UUID.randomUUID()
  val optionApiId = "opt_${optionId.toString().replace("-", "").take(12)}"
  jdbc.update(
    "INSERT INTO tenants (id, api_id, name, slug) VALUES (?, ?, ?, ?)",
    tenantId,
    "ten_${tenantId.toString().replace("-", "").take(12)}",
    "Tenant",
    "tenant-${tenantId.toString().take(8)}",
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
    "severity",
    "Severity",
    "single_select",
  )
  jdbc.update(
    """
    INSERT INTO property_options (id, api_id, tenant_id, property_id, code, label, rank)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    optionId,
    optionApiId,
    tenantId,
    propertyId,
    "critical",
    optionLabel,
    1,
  )
  return SingleSelectPropertySeed(tenantId = tenantId, optionApiId = optionApiId)
}

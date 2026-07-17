package one.ztd.workbench.data.repository.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.spyk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.WorkItemSearchPageRequest
import one.ztd.workbench.agile.workitem.WorkItemSearchScope
import one.ztd.workbench.agile.workitem.query.ConditionNode
import one.ztd.workbench.agile.workitem.query.QueryField
import one.ztd.workbench.agile.workitem.query.QueryOperator
import one.ztd.workbench.agile.workitem.query.QueryValue
import one.ztd.workbench.agile.workitem.query.SortDirection
import one.ztd.workbench.agile.workitem.query.SortTerm
import one.ztd.workbench.agile.workitem.query.WorkItemGroupLabel
import one.ztd.workbench.agile.workitem.query.WorkItemGroupTerm
import one.ztd.workbench.agile.workitem.query.WorkItemQuery
import one.ztd.workbench.agile.workitem.query.WorkItemSearchGroupScope
import one.ztd.workbench.testsupport.postgres.MigrationSpec
import one.ztd.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import org.springframework.jdbc.core.JdbcTemplate

class JdbcWorkItemQueryRepositoryIntegrationTest :
  StringSpec({
    "search filters work items and maps PostgreSQL rows to common result container" {
      withPostgresJdbc { jdbc ->
        val seed = seedWorkItem(jdbc)
        jdbc.update(
          "UPDATE issue_types SET is_active = false, archived_at = now() WHERE id = ?",
          seed.issueTypeId,
        )
        jdbc.update("UPDATE priorities SET is_active = false WHERE id = ?", seed.priorityId)
        jdbc.update(
          "UPDATE issue_statuses SET is_active = false WHERE id = ?",
          seed.statusIdsByGroup.getValue("todo"),
        )
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

        val hit = page.hits.single()
        hit.apiId shouldBe seed.issueApiId
        hit.key shouldBe "CORE-1"
        hit.issueType.name shouldBe "Task"
        hit.status.group shouldBe "todo"
        hit.priority?.name shouldBe "High"
        hit.reporter.displayName shouldBe "Ada"
        hit.assignee.shouldBeNull()
        hit.sprint.shouldBeNull()
        val property = hit.properties.values.single()
        property.property.code shouldBe "storyPoints"
        property.property.dataType shouldBe "number"
        property.value shouldBe JsonPrimitive(8)
        property.displayValue shouldBe JsonPrimitive(8)
      }
    }

    "property presentation resolves scalar and reference values for a page" {
      withPostgresJdbc { jdbc ->
        val seed = seedWorkItem(jdbc)
        val issueId =
          requireNotNull(
            jdbc.queryForObject(
              "SELECT id FROM issues WHERE api_id = ?",
              UUID::class.java,
              seed.issueApiId,
            )
          )
        val userApiId =
          requireNotNull(
            jdbc.queryForObject(
              "SELECT api_id FROM users WHERE id = ?",
              String::class.java,
              seed.userId,
            )
          )
        val projectApiId =
          requireNotNull(
            jdbc.queryForObject(
              "SELECT api_id FROM projects WHERE id = ?",
              String::class.java,
              seed.projectId,
            )
          )

        val text = insertPropertyDefinition(jdbc, seed.tenantId, "impact", "Impact", "text")
        insertPropertyValue(jdbc, seed.tenantId, issueId, text.id, "value_text", "Customer facing")

        val severity =
          insertPropertyDefinition(jdbc, seed.tenantId, "severity", "Severity", "single_select")
        val critical =
          insertPropertyOption(jdbc, seed.tenantId, severity.id, "critical", "Critical")
        insertPropertyValue(
          jdbc,
          seed.tenantId,
          issueId,
          severity.id,
          "value_option_id",
          critical.id,
        )
        jdbc.update("UPDATE property_options SET is_active = false WHERE id = ?", critical.id)

        val labels =
          insertPropertyDefinition(
            jdbc,
            seed.tenantId,
            "labels",
            "Labels",
            "multi_select",
            isArray = true,
          )
        val frontend = insertPropertyOption(jdbc, seed.tenantId, labels.id, "frontend", "Frontend")
        insertJsonPropertyValue(
          jdbc,
          seed.tenantId,
          issueId,
          labels.id,
          "[\"${frontend.apiId}\"]",
        )

        val owner = insertPropertyDefinition(jdbc, seed.tenantId, "owner", "Owner", "user")
        insertPropertyValue(jdbc, seed.tenantId, issueId, owner.id, "value_user_id", seed.userId)

        val reviewers =
          insertPropertyDefinition(
            jdbc,
            seed.tenantId,
            "reviewers",
            "Reviewers",
            "multi_user",
            isArray = true,
          )
        insertJsonPropertyValue(
          jdbc,
          seed.tenantId,
          issueId,
          reviewers.id,
          "[\"$userApiId\"]",
        )

        val project =
          insertPropertyDefinition(jdbc, seed.tenantId, "projectRef", "Project", "project")
        insertPropertyValue(
          jdbc,
          seed.tenantId,
          issueId,
          project.id,
          "value_project_id",
          seed.projectId,
        )

        val issue = insertPropertyDefinition(jdbc, seed.tenantId, "blocks", "Blocks", "issue")
        insertPropertyValue(jdbc, seed.tenantId, issueId, issue.id, "value_issue_id", issueId)

        val hit =
          runBlocking {
              jdbcWorkItemQueryRepository(jdbc)
                .search(WorkItemSearchScope(seed.tenantId, seed.projectId), WorkItemQuery())
            }
            .hits
            .single()
        hit.properties[text.apiId]?.displayValue shouldBe JsonPrimitive("Customer facing")
        hit.properties[severity.apiId]?.value shouldBe JsonPrimitive(critical.apiId)
        hit.properties[severity.apiId]?.displayValue shouldBe
          JsonObject(
            mapOf(
              "id" to JsonPrimitive(critical.apiId),
              "code" to JsonPrimitive("critical"),
              "label" to JsonPrimitive("Critical"),
              "color" to JsonPrimitive("#dc2626"),
            )
          )
        hit.properties[labels.apiId]?.value shouldBe
          JsonArray(listOf(JsonPrimitive(frontend.apiId)))
        (hit.properties[labels.apiId]?.displayValue as JsonArray).single() shouldBe
          JsonObject(
            mapOf(
              "id" to JsonPrimitive(frontend.apiId),
              "code" to JsonPrimitive("frontend"),
              "label" to JsonPrimitive("Frontend"),
              "color" to JsonPrimitive("#dc2626"),
            )
          )
        hit.properties[owner.apiId]?.value shouldBe JsonPrimitive(userApiId)
        hit.properties[owner.apiId]?.displayValue shouldBe
          JsonObject(
            mapOf(
              "id" to JsonPrimitive(userApiId),
              "displayName" to JsonPrimitive("Ada"),
            )
          )
        hit.properties[reviewers.apiId]?.value shouldBe JsonArray(listOf(JsonPrimitive(userApiId)))
        (hit.properties[reviewers.apiId]?.displayValue as JsonArray).single() shouldBe
          JsonObject(
            mapOf(
              "id" to JsonPrimitive(userApiId),
              "displayName" to JsonPrimitive("Ada"),
            )
          )
        hit.properties[project.apiId]?.value shouldBe JsonPrimitive(projectApiId)
        hit.properties[project.apiId]?.displayValue shouldBe
          JsonObject(
            mapOf(
              "id" to JsonPrimitive(projectApiId),
              "identifier" to JsonPrimitive("CORE"),
              "name" to JsonPrimitive("Core"),
            )
          )
        hit.properties[issue.apiId]?.value shouldBe JsonPrimitive(seed.issueApiId)
        hit.properties[issue.apiId]?.displayValue shouldBe
          JsonObject(
            mapOf(
              "id" to JsonPrimitive(seed.issueApiId),
              "key" to JsonPrimitive("CORE-1"),
              "title" to JsonPrimitive("First issue"),
            )
          )
      }
    }

    "search cursor pagination returns distinct pages without duplicates" {
      withPostgresJdbc { jdbc ->
        val seed = seedWorkItems(jdbc, count = 3)
        val propertyLoader = spyk(WorkItemPropertyPresentationLoader(jdbc))
        val repository = jdbcWorkItemQueryRepository(jdbc, propertyLoader)
        val scope = WorkItemSearchScope(tenantId = seed.tenantId, projectId = seed.projectId)
        val query =
          WorkItemQuery(sort = listOf(SortTerm(QueryField.System("key"), SortDirection.ASC)))

        val first = runBlocking {
          repository.search(scope, query, page = WorkItemSearchPageRequest(limit = 2))
        }
        first.hits shouldHaveSize 2
        first.nextCursor.shouldNotBeNull()
        verify(exactly = 1) { propertyLoader.load(seed.tenantId, match { it.size == 2 }) }

        val second = runBlocking {
          repository.search(
            scope,
            query,
            page = WorkItemSearchPageRequest(limit = 2, cursor = first.nextCursor),
          )
        }
        second.hits shouldHaveSize 1
        second.nextCursor.shouldBeNull()

        (first.hits + second.hits).map { it.key }.toSet() shouldHaveSize 3
      }
    }

    "searchGroups returns buckets for group field" {
      withPostgresJdbc { jdbc ->
        val seed = seedWorkItems(jdbc, count = 2, statusGroups = listOf("todo", "done"))
        val repository = jdbcWorkItemQueryRepository(jdbc)
        val query =
          WorkItemQuery(group = WorkItemGroupTerm(field = QueryField.System("statusGroup")))

        val page = runBlocking {
          repository.searchGroups(
            scope = WorkItemSearchScope(tenantId = seed.tenantId, projectId = seed.projectId),
            query = query,
          )
        }

        page.groups shouldHaveSize 2
        page.groups
          .map { it.label }
          .shouldContainExactlyInAnyOrder(
            WorkItemGroupLabel.Text("Todo"),
            WorkItemGroupLabel.Text("Done"),
          )
        page.nextGroupCursor.shouldBeNull()
      }
    }

    "searchGroups returns property single_select option labels" {
      withPostgresJdbc { jdbc ->
        val seed = seedSingleSelectGroupedWorkItems(jdbc)
        val repository = jdbcWorkItemQueryRepository(jdbc)
        val query =
          WorkItemQuery(
            group = WorkItemGroupTerm(field = QueryField.Property(apiId = null, code = "severity"))
          )

        val page = runBlocking {
          repository.searchGroups(
            scope = WorkItemSearchScope(tenantId = seed.tenantId, projectId = seed.projectId),
            query = query,
          )
        }

        page.groups shouldHaveSize 2
        page.groups
          .map { it.label }
          .shouldContainExactlyInAnyOrder(
            WorkItemGroupLabel.Text("Critical"),
            WorkItemGroupLabel.Text("Low"),
          )
      }
    }

    "search includeGroupKeys filters to selected bucket" {
      withPostgresJdbc { jdbc ->
        val seed = seedWorkItems(jdbc, count = 2, statusGroups = listOf("todo", "done"))
        val repository = jdbcWorkItemQueryRepository(jdbc)
        val query =
          WorkItemQuery(
            group = WorkItemGroupTerm(field = QueryField.System("statusGroup")),
            sort = listOf(SortTerm(QueryField.System("key"), SortDirection.ASC)),
          )
        val scope =
          WorkItemSearchGroupScope(
            includeGroupKeys =
              listOf(
                ConditionNode.Predicate(
                  field = QueryField.System("statusGroup"),
                  op = QueryOperator.EQ,
                  value = QueryValue.Literal(JsonPrimitive("todo")),
                )
              )
          )

        val page = runBlocking {
          repository.search(
            scope = WorkItemSearchScope(tenantId = seed.tenantId, projectId = seed.projectId),
            query = query,
            groupScope = scope,
          )
        }

        page.hits shouldHaveSize 1
        page.hits.single().status.group shouldBe "todo"
      }
    }
  })

private data class SeededSingleSelectGroup(
  val tenantId: UUID,
  val projectId: UUID,
)

private data class SeededProperty(val id: UUID, val apiId: String)

private fun insertPropertyDefinition(
  jdbc: JdbcTemplate,
  tenantId: UUID,
  code: String,
  name: String,
  dataType: String,
  isArray: Boolean = false,
): SeededProperty {
  val id = UUID.randomUUID()
  val apiId = "fld_${id.toString().replace("-", "").take(12)}"
  jdbc.update(
    """
    INSERT INTO property_definitions (id, api_id, tenant_id, code, name, data_type, is_array)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    id,
    apiId,
    tenantId,
    code,
    name,
    dataType,
    isArray,
  )
  return SeededProperty(id, apiId)
}

private fun insertPropertyOption(
  jdbc: JdbcTemplate,
  tenantId: UUID,
  propertyId: UUID,
  code: String,
  label: String,
): SeededProperty {
  val id = UUID.randomUUID()
  val apiId = "opt_${id.toString().replace("-", "").take(12)}"
  jdbc.update(
    """
    INSERT INTO property_options (id, api_id, tenant_id, property_id, code, label, color)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    id,
    apiId,
    tenantId,
    propertyId,
    code,
    label,
    "#dc2626",
  )
  return SeededProperty(id, apiId)
}

private fun insertPropertyValue(
  jdbc: JdbcTemplate,
  tenantId: UUID,
  issueId: UUID,
  propertyId: UUID,
  valueColumn: String,
  value: Any,
) {
  require(valueColumn in ALLOWED_PROPERTY_VALUE_COLUMNS)
  jdbc.update(
    "INSERT INTO issue_property_values (id, tenant_id, issue_id, property_id, $valueColumn) " +
      "VALUES (?, ?, ?, ?, ?)",
    UUID.randomUUID(),
    tenantId,
    issueId,
    propertyId,
    value,
  )
}

private fun insertJsonPropertyValue(
  jdbc: JdbcTemplate,
  tenantId: UUID,
  issueId: UUID,
  propertyId: UUID,
  value: String,
) {
  jdbc.update(
    """
    INSERT INTO issue_property_values (id, tenant_id, issue_id, property_id, value_array)
    VALUES (?, ?, ?, ?, ?::jsonb)
    """
      .trimIndent(),
    UUID.randomUUID(),
    tenantId,
    issueId,
    propertyId,
    value,
  )
}

private val ALLOWED_PROPERTY_VALUE_COLUMNS =
  setOf("value_text", "value_option_id", "value_user_id", "value_project_id", "value_issue_id")

private fun seedSingleSelectGroupedWorkItems(jdbc: JdbcTemplate): SeededSingleSelectGroup {
  val base = seedWorkItem(jdbc)
  val severityPropertyId = UUID.randomUUID()
  val criticalOptionId = UUID.randomUUID()
  val lowOptionId = UUID.randomUUID()
  val criticalOptionApiId = "opt_${criticalOptionId.toString().replace("-", "").take(12)}"
  val lowOptionApiId = "opt_${lowOptionId.toString().replace("-", "").take(12)}"
  jdbc.update(
    """
    INSERT INTO property_definitions (id, api_id, tenant_id, code, name, data_type)
    VALUES (?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    severityPropertyId,
    "fld_${severityPropertyId.toString().replace("-", "").take(12)}",
    base.tenantId,
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
    criticalOptionId,
    criticalOptionApiId,
    base.tenantId,
    severityPropertyId,
    "critical",
    "Critical",
    1,
  )
  jdbc.update(
    """
    INSERT INTO property_options (id, api_id, tenant_id, property_id, code, label, rank)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    lowOptionId,
    lowOptionApiId,
    base.tenantId,
    severityPropertyId,
    "low",
    "Low",
    2,
  )
  val firstIssueId =
    jdbc.queryForObject(
      "SELECT id FROM issues WHERE api_id = ?",
      UUID::class.java,
      base.issueApiId,
    )
  jdbc.update(
    """
    INSERT INTO issue_property_values (id, tenant_id, issue_id, property_id, value_option_id)
    VALUES (?, ?, ?, ?, ?)
    """
      .trimIndent(),
    UUID.randomUUID(),
    base.tenantId,
    firstIssueId,
    severityPropertyId,
    criticalOptionId,
  )
  val secondIssueId = UUID.randomUUID()
  val secondIssueApiId = "iss_${secondIssueId.toString().replace("-", "").take(12)}"
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
    secondIssueId,
    secondIssueApiId,
    base.tenantId,
    base.projectId,
    base.issueTypeId,
    base.issueTypeConfigId,
    2L,
    "Second issue",
    base.statusIdsByGroup.values.first(),
    base.priorityId,
    base.userId,
    base.userId,
    """{"severity":"$lowOptionApiId"}""",
  )
  jdbc.update(
    """
    INSERT INTO issue_key_aliases (id, tenant_id, project_id, issue_id, issue_key, project_identifier, sequence_no)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    UUID.randomUUID(),
    base.tenantId,
    base.projectId,
    secondIssueId,
    "CORE-2",
    "CORE",
    2L,
  )
  jdbc.update(
    """
    INSERT INTO issue_property_values (id, tenant_id, issue_id, property_id, value_option_id)
    VALUES (?, ?, ?, ?, ?)
    """
      .trimIndent(),
    UUID.randomUUID(),
    base.tenantId,
    secondIssueId,
    severityPropertyId,
    lowOptionId,
  )
  return SeededSingleSelectGroup(tenantId = base.tenantId, projectId = base.projectId)
}

private data class SeededWorkItems(
  val tenantId: UUID,
  val projectId: UUID,
  val issueApiIds: List<String>,
)

private data class WorkItemStackSeed(
  val tenantId: UUID,
  val projectId: UUID,
  val issueTypeId: UUID,
  val issueTypeConfigId: UUID,
  val priorityId: UUID,
  val userId: UUID,
  val propertyId: UUID,
  val issueApiId: String,
  val statusIdsByGroup: MutableMap<String, UUID>,
)

private data class SeededWorkItemWithStack(
  val tenantId: UUID,
  val projectId: UUID,
  val issueApiId: String,
  val issueTypeId: UUID,
  val issueTypeConfigId: UUID,
  val priorityId: UUID,
  val userId: UUID,
  val propertyId: UUID,
  val statusIdsByGroup: MutableMap<String, UUID>,
)

private fun withPostgresJdbc(block: (JdbcTemplate) -> Unit) {
  WorkbenchPostgresTestSupport.withJdbcTemplate(MigrationSpec.Core, block)
}

private fun seedWorkItems(
  jdbc: JdbcTemplate,
  count: Int = 1,
  statusGroups: List<String> = List(count) { "todo" },
): SeededWorkItems {
  val base = seedWorkItem(jdbc, statusGroups.first())
  val issueApiIds = mutableListOf(base.issueApiId)
  val stack =
    WorkItemStackSeed(
      tenantId = base.tenantId,
      projectId = base.projectId,
      issueTypeId = base.issueTypeId,
      issueTypeConfigId = base.issueTypeConfigId,
      priorityId = base.priorityId,
      userId = base.userId,
      propertyId = base.propertyId,
      issueApiId = base.issueApiId,
      statusIdsByGroup = base.statusIdsByGroup,
    )
  statusGroups.drop(1).forEachIndexed { index, statusGroup ->
    issueApiIds +=
      insertIssue(
        jdbc = jdbc,
        stack = stack,
        sequenceNo = index + 2L,
        issueKey = "CORE-${index + 2}",
        statusGroup = statusGroup,
      )
  }
  return SeededWorkItems(
    tenantId = base.tenantId,
    projectId = base.projectId,
    issueApiIds = issueApiIds,
  )
}

private fun insertIssue(
  jdbc: JdbcTemplate,
  stack: WorkItemStackSeed,
  sequenceNo: Long,
  issueKey: String,
  statusGroup: String,
): String {
  val statusId =
    stack.statusIdsByGroup.getOrPut(statusGroup) {
      val newStatusId = UUID.randomUUID()
      jdbc.update(
        """
        INSERT INTO issue_statuses (id, api_id, tenant_id, code, name, status_group)
        VALUES (?, ?, ?, ?, ?, ?)
        """
          .trimIndent(),
        newStatusId,
        "sts_${newStatusId.toString().replace("-", "").take(12)}",
        stack.tenantId,
        statusGroup,
        statusGroup.replaceFirstChar { it.uppercase() },
        statusGroup,
      )
      newStatusId
    }
  val issueId = UUID.randomUUID()
  val issueApiId = "iss_${issueId.toString().replace("-", "").take(12)}"
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
    stack.tenantId,
    stack.projectId,
    stack.issueTypeId,
    stack.issueTypeConfigId,
    sequenceNo,
    "Issue $sequenceNo",
    statusId,
    stack.priorityId,
    stack.userId,
    stack.userId,
    """{"storyPoints":8}""",
  )
  jdbc.update(
    """
    INSERT INTO issue_key_aliases (id, tenant_id, project_id, issue_id, issue_key, project_identifier, sequence_no)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """
      .trimIndent(),
    UUID.randomUUID(),
    stack.tenantId,
    stack.projectId,
    issueId,
    issueKey,
    "CORE",
    sequenceNo,
  )
  jdbc.update(
    """
    INSERT INTO issue_property_values (id, tenant_id, issue_id, property_id, value_number)
    VALUES (?, ?, ?, ?, ?)
    """
      .trimIndent(),
    UUID.randomUUID(),
    stack.tenantId,
    issueId,
    stack.propertyId,
    8,
  )
  return issueApiId
}

private fun seedWorkItem(
  jdbc: JdbcTemplate,
  statusGroup: String = "todo",
): SeededWorkItemWithStack {
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
    statusGroup,
    statusGroup.replaceFirstChar { it.uppercase() },
    statusGroup,
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
  return SeededWorkItemWithStack(
    tenantId = tenantId,
    projectId = projectId,
    issueApiId = issueApiId,
    issueTypeId = issueTypeId,
    issueTypeConfigId = issueTypeConfigId,
    priorityId = priorityId,
    userId = userId,
    propertyId = propertyId,
    statusIdsByGroup = mutableMapOf(statusGroup to statusId),
  )
}

private fun jdbcWorkItemQueryRepository(
  jdbc: JdbcTemplate,
  propertyLoader: WorkItemPropertyPresentationLoader = WorkItemPropertyPresentationLoader(jdbc),
) =
  JdbcWorkItemQueryRepository(
    jdbc,
    Dispatchers.IO,
    WorkItemGroupLabelResolver(jdbc),
    propertyLoader,
  )

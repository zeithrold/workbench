package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.workitem.WorkItemSearchPageRequest
import ink.doa.workbench.core.workitem.WorkItemSearchScope
import ink.doa.workbench.core.workitem.query.ConditionNode
import ink.doa.workbench.core.workitem.query.QueryField
import ink.doa.workbench.core.workitem.query.QueryOperator
import ink.doa.workbench.core.workitem.query.QueryValue
import ink.doa.workbench.core.workitem.query.SortDirection
import ink.doa.workbench.core.workitem.query.SortTerm
import ink.doa.workbench.core.workitem.query.WorkItemGroupLabel
import ink.doa.workbench.core.workitem.query.WorkItemGroupTerm
import ink.doa.workbench.core.workitem.query.WorkItemQuery
import ink.doa.workbench.core.workitem.query.WorkItemSearchGroupScope
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Tag
import org.springframework.jdbc.core.JdbcTemplate

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

        page.hits.single().apiId shouldBe seed.issueApiId
        page.hits.single().key shouldBe "CORE-1"
        page.hits.single().statusGroup shouldBe "todo"
        page.hits.single().properties["storyPoints"] shouldBe JsonPrimitive(8)
      }
    }

    "search cursor pagination returns distinct pages without duplicates" {
      withPostgresJdbc { jdbc ->
        val seed = seedWorkItems(jdbc, count = 3)
        val repository = jdbcWorkItemQueryRepository(jdbc)
        val scope = WorkItemSearchScope(tenantId = seed.tenantId, projectId = seed.projectId)
        val query =
          WorkItemQuery(sort = listOf(SortTerm(QueryField.System("key"), SortDirection.ASC)))

        val first = runBlocking {
          repository.search(scope, query, page = WorkItemSearchPageRequest(limit = 2))
        }
        first.hits shouldHaveSize 2
        first.nextCursor.shouldNotBeNull()

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
        page.hits.single().statusGroup shouldBe "todo"
      }
    }
  })

private data class SeededSingleSelectGroup(
  val tenantId: UUID,
  val projectId: UUID,
)

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

@Suppress("InjectDispatcher")
private fun jdbcWorkItemQueryRepository(jdbc: JdbcTemplate) =
  JdbcWorkItemQueryRepository(jdbc, Dispatchers.IO, WorkItemGroupLabelResolver(jdbc))

package ink.doa.workbench.data.support

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.project.model.CreateProjectCommand
import ink.doa.workbench.core.workitem.activity.WorkItemActivityCodec
import ink.doa.workbench.core.workitem.model.CreateIssueStatusCommand
import ink.doa.workbench.core.workitem.model.CreateIssueTypeCommand
import ink.doa.workbench.core.workitem.model.CreateIssueTypeConfigCommand
import ink.doa.workbench.core.workitem.model.CreateWorkflowCommand
import ink.doa.workbench.core.workitem.model.IssueTypeConfigStatusInput
import ink.doa.workbench.core.workitem.model.WorkItemConfigScope
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.persistence.postgres.identity.UsersTable
import ink.doa.workbench.data.repository.project.ExposedProjectRepository
import ink.doa.workbench.data.repository.workitem.ExposedIssueTypeConfigRepository
import ink.doa.workbench.data.repository.workitem.ExposedWorkItemActivityRepository
import ink.doa.workbench.data.repository.workitem.ExposedWorkItemCatalogRepository
import ink.doa.workbench.data.repository.workitem.ExposedWorkItemCommentRepository
import ink.doa.workbench.data.repository.workitem.ExposedWorkItemRepository
import ink.doa.workbench.data.repository.workitem.ExposedWorkItemTimelineRepository
import ink.doa.workbench.data.repository.workitem.ExposedWorkflowConfigurationRepository
import ink.doa.workbench.data.repository.workitem.WorkItemActivityFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonObject
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer

internal fun withPostgresDatabase(block: suspend (Database) -> Unit) {
  PostgreSQLContainer("postgres:18-alpine").use { postgres ->
    postgres.start()
    Flyway.configure()
      .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
      .locations("classpath:db/migration", "classpath:ink/doa/workbench/data/migration")
      .load()
      .migrate()
    val database =
      Database.connect(
        url = postgres.jdbcUrl,
        driver = "org.postgresql.Driver",
        user = postgres.username,
        password = postgres.password,
      )
    kotlinx.coroutines.runBlocking { block(database) }
  }
}

internal fun seedTenant(database: Database): UUID {
  val tenantId = UUID.randomUUID()
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  transaction(database) {
    TenantsTable.insert {
      it[id] = tenantId.toKotlinUuid()
      it[apiId] = PublicId.new("ten").value
      it[name] = "Test Tenant"
      it[slug] = "test-${tenantId.toString().take(8)}"
      it[timezone] = "UTC"
      it[locale] = "en-US"
      it[createdAt] = now
      it[updatedAt] = now
    }
  }
  return tenantId
}

internal fun seedUser(database: Database): UUID {
  val userId = UUID.randomUUID()
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  transaction(database) {
    UsersTable.insert {
      it[id] = userId.toKotlinUuid()
      it[apiId] = PublicId.new("usr").value
      it[displayName] = "Ada"
      it[primaryEmail] = "ada-${userId.toString().take(8)}@example.test"
      it[createdAt] = now
      it[updatedAt] = now
    }
  }
  return userId
}

internal suspend fun seedWorkItemStack(database: Database): WorkItemStack {
  val tenantId = seedTenant(database)
  val actorId = seedUser(database)
  val projects = ExposedProjectRepository(database)
  val project =
    projects.create(
      CreateProjectCommand(
        tenantId = tenantId,
        identifier = "WB",
        name = "Workbench",
        description = null,
        createdBy = actorId,
        leadUserId = actorId,
      )
    )
  val catalog = ExposedWorkItemCatalogRepository(database)
  val todoStatus =
    catalog.createStatus(
      CreateIssueStatusCommand(
        tenantId = tenantId,
        code = "todo",
        name = "To Do",
        statusGroup = WorkItemStatusGroup.TODO,
        rank = 10,
      )
    )
  val doneStatus =
    catalog.createStatus(
      CreateIssueStatusCommand(
        tenantId = tenantId,
        code = "done",
        name = "Done",
        statusGroup = WorkItemStatusGroup.DONE,
        rank = 20,
        isTerminal = true,
      )
    )
  val issueType =
    catalog.createIssueType(
      CreateIssueTypeCommand(
        tenantId = tenantId,
        scope = WorkItemConfigScope.TENANT,
        code = "task",
        name = "Task",
      )
    )
  val workflows = ExposedWorkflowConfigurationRepository(database, catalog)
  val workflow =
    workflows.createWorkflow(
      CreateWorkflowCommand(
        tenantId = tenantId,
        code = "default",
        name = "Default",
        createdBy = actorId,
      )
    )
  val issueTypeConfigs = ExposedIssueTypeConfigRepository(database, catalog, workflows)
  val config =
    issueTypeConfigs.createConfig(
      CreateIssueTypeConfigCommand(
        tenantId = tenantId,
        scope = WorkItemConfigScope.TENANT,
        projectId = null,
        issueTypeApiId = issueType.apiId.value,
        workflowApiId = workflow.apiId.value,
        createdBy = actorId,
        createFields = JsonObject(emptyMap()),
        statuses =
          listOf(
            IssueTypeConfigStatusInput(
              statusApiId = todoStatus.apiId.value,
              isInitial = true,
            ),
            IssueTypeConfigStatusInput(
              statusApiId = doneStatus.apiId.value,
              isTerminal = true,
            ),
          ),
      )
    )
  return WorkItemStack(
    tenantId = tenantId,
    actorId = actorId,
    projectId = project.id,
    issueType = issueType,
    workflow = workflow,
    todoStatus = todoStatus,
    doneStatus = doneStatus,
    config = config,
  )
}

internal fun workItemRepository(database: Database): ExposedWorkItemRepository {
  val codec = WorkItemActivityCodec()
  val factory = WorkItemActivityFactory()
  return ExposedWorkItemRepository(database, factory, codec)
}

internal fun workItemCommentRepository(database: Database): ExposedWorkItemCommentRepository {
  val codec = WorkItemActivityCodec()
  val factory = WorkItemActivityFactory()
  return ExposedWorkItemCommentRepository(database, factory, codec)
}

internal fun workItemTimelineRepository(database: Database): ExposedWorkItemTimelineRepository {
  val codec = WorkItemActivityCodec()
  val factory = WorkItemActivityFactory()
  val activities = ExposedWorkItemActivityRepository(database, codec)
  val comments = ExposedWorkItemCommentRepository(database, factory, codec)
  return ExposedWorkItemTimelineRepository(database, activities, comments)
}

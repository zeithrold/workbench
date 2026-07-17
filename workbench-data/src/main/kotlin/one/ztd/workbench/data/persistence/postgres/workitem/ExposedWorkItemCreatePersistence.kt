package one.ztd.workbench.data.persistence.postgres.workitem

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import one.ztd.workbench.agile.workitem.model.CreateWorkItemCommand
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyValue
import one.ztd.workbench.kernel.common.ids.PublicId
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

internal fun prepareWorkItemInsert(
  command: CreateWorkItemCommand,
  propertyValues: List<WorkItemPropertyValue>,
): PreparedWorkItemInsert {
  val project = requireProject(command.tenantId, command.projectId)
  val sequence = allocateSequence(project.id)
  val issueId = UUID.randomUUID()
  val issueApiId = PublicId.new("iss")
  val now = now()
  return PreparedWorkItemInsert(
    issueId = issueId,
    issueApiId = issueApiId,
    key = "${project.identifier}-$sequence",
    project = project,
    priorityId = command.priorityApiId?.let { resolvePriority(command.tenantId, it) },
    assigneeId = command.assigneeApiId?.let { resolveUser(it) },
    sprintId = command.sprintApiId?.let { resolveSprint(command.tenantId, command.projectId, it) },
    snapshot = snapshot(propertyValues),
    sequence = sequence,
    now = now,
  )
}

internal data class InsertWorkItemRowsCommand(
  val command: CreateWorkItemCommand,
  val prepared: PreparedWorkItemInsert,
  val issueTypeId: UUID,
  val issueTypeConfigId: UUID,
  val initialStatusId: UUID,
  val propertyValues: List<WorkItemPropertyValue>,
)

internal data class InsertHierarchyLinkCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val parentIssueId: UUID,
  val childIssueId: UUID,
  val actorUserId: UUID,
  val createdAt: OffsetDateTime,
)

internal fun insertWorkItemRows(command: InsertWorkItemRowsCommand) {
  val prepared = command.prepared
  IssuesTable.insert {
    it[IssuesTable.id] = prepared.issueId.toKotlinUuid()
    it[IssuesTable.apiId] = prepared.issueApiId.value
    it[IssuesTable.tenantId] = command.command.tenantId.toKotlinUuid()
    it[IssuesTable.projectId] = command.command.projectId.toKotlinUuid()
    it[IssuesTable.issueTypeId] = command.issueTypeId.toKotlinUuid()
    it[IssuesTable.issueTypeConfigId] = command.issueTypeConfigId.toKotlinUuid()
    it[IssuesTable.sequenceNo] = prepared.sequence
    it[IssuesTable.title] = command.command.title
    it[IssuesTable.description] = command.command.description
    it[IssuesTable.descriptionPlainText] = command.command.descriptionPlainText
    it[IssuesTable.statusId] = command.initialStatusId.toKotlinUuid()
    it[IssuesTable.priorityId] = prepared.priorityId?.toKotlinUuid()
    it[IssuesTable.reporterId] = command.command.reporterId.toKotlinUuid()
    it[IssuesTable.assigneeId] = prepared.assigneeId?.toKotlinUuid()
    it[IssuesTable.sprintId] = prepared.sprintId?.toKotlinUuid()
    it[IssuesTable.propertiesSnapshot] = prepared.snapshot
    it[IssuesTable.createdBy] = command.command.actorUserId.toKotlinUuid()
    it[IssuesTable.createdAt] = prepared.now
    it[IssuesTable.updatedAt] = prepared.now
  }
  IssueKeyAliasesTable.insert {
    it[IssueKeyAliasesTable.id] = UUID.randomUUID().toKotlinUuid()
    it[IssueKeyAliasesTable.tenantId] = command.command.tenantId.toKotlinUuid()
    it[IssueKeyAliasesTable.projectId] = command.command.projectId.toKotlinUuid()
    it[IssueKeyAliasesTable.issueId] = prepared.issueId.toKotlinUuid()
    it[IssueKeyAliasesTable.issueKey] = prepared.key
    it[IssueKeyAliasesTable.projectIdentifier] = prepared.project.identifier
    it[IssueKeyAliasesTable.sequenceNo] = prepared.sequence
    it[IssueKeyAliasesTable.isCurrent] = true
    it[IssueKeyAliasesTable.createdAt] = prepared.now
    it[IssueKeyAliasesTable.createdBy] = command.command.actorUserId.toKotlinUuid()
  }
  insertPropertyValues(
    command.command.tenantId,
    prepared.issueId,
    command.propertyValues,
    command.command.actorUserId,
    prepared.now,
  )
}

internal fun insertHierarchyLink(command: InsertHierarchyLinkCommand) {
  val nextRank =
    IssueHierarchyTable.selectAll()
      .where {
        (IssueHierarchyTable.tenantId eq command.tenantId.toKotlinUuid()) and
          (IssueHierarchyTable.parentIssueId eq command.parentIssueId.toKotlinUuid())
      }
      .orderBy(IssueHierarchyTable.rank to SortOrder.DESC)
      .limit(1)
      .singleOrNull()
      ?.get(IssueHierarchyTable.rank)
      ?.plus(100) ?: 100
  IssueHierarchyTable.insert {
    it[IssueHierarchyTable.id] = UUID.randomUUID().toKotlinUuid()
    it[IssueHierarchyTable.tenantId] = command.tenantId.toKotlinUuid()
    it[IssueHierarchyTable.projectId] = command.projectId.toKotlinUuid()
    it[IssueHierarchyTable.parentIssueId] = command.parentIssueId.toKotlinUuid()
    it[IssueHierarchyTable.childIssueId] = command.childIssueId.toKotlinUuid()
    it[IssueHierarchyTable.rank] = nextRank
    it[IssueHierarchyTable.createdBy] = command.actorUserId.toKotlinUuid()
    it[IssueHierarchyTable.createdAt] = command.createdAt
  }
}

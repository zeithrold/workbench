package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.data.persistence.IssueHierarchyTable
import ink.doa.workbench.data.persistence.IssueKeyAliasesTable
import ink.doa.workbench.data.persistence.IssuesTable
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
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

internal fun insertWorkItemRows(
  command: CreateWorkItemCommand,
  prepared: PreparedWorkItemInsert,
  issueTypeId: UUID,
  issueTypeConfigId: UUID,
  initialStatusId: UUID,
  propertyValues: List<WorkItemPropertyValue>,
) {
  IssuesTable.insert {
    it[IssuesTable.id] = prepared.issueId.toKotlinUuid()
    it[IssuesTable.apiId] = prepared.issueApiId.value
    it[IssuesTable.tenantId] = command.tenantId.toKotlinUuid()
    it[IssuesTable.projectId] = command.projectId.toKotlinUuid()
    it[IssuesTable.issueTypeId] = issueTypeId.toKotlinUuid()
    it[IssuesTable.issueTypeConfigId] = issueTypeConfigId.toKotlinUuid()
    it[IssuesTable.sequenceNo] = prepared.sequence
    it[IssuesTable.title] = command.title
    it[IssuesTable.description] = command.description
    it[IssuesTable.descriptionPlainText] = command.descriptionPlainText
    it[IssuesTable.statusId] = initialStatusId.toKotlinUuid()
    it[IssuesTable.priorityId] = prepared.priorityId?.toKotlinUuid()
    it[IssuesTable.reporterId] = command.reporterId.toKotlinUuid()
    it[IssuesTable.assigneeId] = prepared.assigneeId?.toKotlinUuid()
    it[IssuesTable.sprintId] = prepared.sprintId?.toKotlinUuid()
    it[IssuesTable.propertiesSnapshot] = prepared.snapshot
    it[IssuesTable.createdBy] = command.actorUserId.toKotlinUuid()
    it[IssuesTable.createdAt] = prepared.now
    it[IssuesTable.updatedAt] = prepared.now
  }
  IssueKeyAliasesTable.insert {
    it[IssueKeyAliasesTable.id] = UUID.randomUUID().toKotlinUuid()
    it[IssueKeyAliasesTable.tenantId] = command.tenantId.toKotlinUuid()
    it[IssueKeyAliasesTable.projectId] = command.projectId.toKotlinUuid()
    it[IssueKeyAliasesTable.issueId] = prepared.issueId.toKotlinUuid()
    it[IssueKeyAliasesTable.issueKey] = prepared.key
    it[IssueKeyAliasesTable.projectIdentifier] = prepared.project.identifier
    it[IssueKeyAliasesTable.sequenceNo] = prepared.sequence
    it[IssueKeyAliasesTable.isCurrent] = true
    it[IssueKeyAliasesTable.createdAt] = prepared.now
    it[IssueKeyAliasesTable.createdBy] = command.actorUserId.toKotlinUuid()
  }
  insertPropertyValues(
    command.tenantId,
    prepared.issueId,
    propertyValues,
    command.actorUserId,
    prepared.now,
  )
}

internal fun insertHierarchyLink(
  tenantId: UUID,
  projectId: UUID,
  parentIssueId: UUID,
  childIssueId: UUID,
  actorUserId: UUID,
  createdAt: OffsetDateTime,
) {
  val nextRank =
    IssueHierarchyTable.selectAll()
      .where {
        (IssueHierarchyTable.tenantId eq tenantId.toKotlinUuid()) and
          (IssueHierarchyTable.parentIssueId eq parentIssueId.toKotlinUuid())
      }
      .orderBy(IssueHierarchyTable.rank to SortOrder.DESC)
      .limit(1)
      .singleOrNull()
      ?.get(IssueHierarchyTable.rank)
      ?.plus(100) ?: 100
  IssueHierarchyTable.insert {
    it[IssueHierarchyTable.id] = UUID.randomUUID().toKotlinUuid()
    it[IssueHierarchyTable.tenantId] = tenantId.toKotlinUuid()
    it[IssueHierarchyTable.projectId] = projectId.toKotlinUuid()
    it[IssueHierarchyTable.parentIssueId] = parentIssueId.toKotlinUuid()
    it[IssueHierarchyTable.childIssueId] = childIssueId.toKotlinUuid()
    it[IssueHierarchyTable.rank] = nextRank
    it[IssueHierarchyTable.createdBy] = actorUserId.toKotlinUuid()
    it[IssueHierarchyTable.createdAt] = createdAt
  }
}

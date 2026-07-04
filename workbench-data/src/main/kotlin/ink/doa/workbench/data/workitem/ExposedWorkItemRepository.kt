@file:Suppress("TooManyFunctions", "LongMethod")

package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemPropertyDataType
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.data.persistence.IssueHierarchyTable
import ink.doa.workbench.data.persistence.IssueKeyAliasesTable
import ink.doa.workbench.data.persistence.IssuePropertyValuesTable
import ink.doa.workbench.data.persistence.IssueStatusHistoryTable
import ink.doa.workbench.data.persistence.IssueStatusesTable
import ink.doa.workbench.data.persistence.IssueTypeConfigsTable
import ink.doa.workbench.data.persistence.IssueTypesTable
import ink.doa.workbench.data.persistence.IssuesTable
import ink.doa.workbench.data.persistence.PrioritiesTable
import ink.doa.workbench.data.persistence.ProjectsTable
import ink.doa.workbench.data.persistence.PropertyOptionsTable
import ink.doa.workbench.data.persistence.SprintsTable
import ink.doa.workbench.data.persistence.UsersTable
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedWorkItemRepository(private val database: Database) : WorkItemRepository {
  override suspend fun create(
    command: CreateWorkItemCommand,
    issueTypeId: UUID,
    issueTypeConfigId: UUID,
    initialStatusId: UUID,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult =
    suspendTransaction(db = database) {
      val project = requireProject(command.tenantId, command.projectId)
      val sequence = allocateSequence(project.id)
      val issueId = UUID.randomUUID()
      val issueApiId = PublicId.new("iss")
      val key = "${project.identifier}-$sequence"
      val now = now()
      val priorityId = command.priorityApiId?.let { resolvePriority(command.tenantId, it) }
      val assigneeId = command.assigneeApiId?.let { resolveUser(it) }
      val sprintId =
        command.sprintApiId?.let { resolveSprint(command.tenantId, command.projectId, it) }
      val snapshot = snapshot(propertyValues)
      IssuesTable.insert {
        it[IssuesTable.id] = issueId.toKotlinUuid()
        it[IssuesTable.apiId] = issueApiId.value
        it[IssuesTable.tenantId] = command.tenantId.toKotlinUuid()
        it[IssuesTable.projectId] = command.projectId.toKotlinUuid()
        it[IssuesTable.issueTypeId] = issueTypeId.toKotlinUuid()
        it[IssuesTable.issueTypeConfigId] = issueTypeConfigId.toKotlinUuid()
        it[IssuesTable.sequenceNo] = sequence
        it[IssuesTable.title] = command.title
        it[IssuesTable.description] = command.description
        it[IssuesTable.statusId] = initialStatusId.toKotlinUuid()
        it[IssuesTable.priorityId] = priorityId?.toKotlinUuid()
        it[IssuesTable.reporterId] = command.reporterId.toKotlinUuid()
        it[IssuesTable.assigneeId] = assigneeId?.toKotlinUuid()
        it[IssuesTable.sprintId] = sprintId?.toKotlinUuid()
        it[IssuesTable.propertiesSnapshot] = snapshot
        it[IssuesTable.createdBy] = command.actorUserId.toKotlinUuid()
        it[IssuesTable.createdAt] = now
        it[IssuesTable.updatedAt] = now
      }
      IssueKeyAliasesTable.insert {
        it[IssueKeyAliasesTable.id] = UUID.randomUUID().toKotlinUuid()
        it[IssueKeyAliasesTable.tenantId] = command.tenantId.toKotlinUuid()
        it[IssueKeyAliasesTable.projectId] = command.projectId.toKotlinUuid()
        it[IssueKeyAliasesTable.issueId] = issueId.toKotlinUuid()
        it[IssueKeyAliasesTable.issueKey] = key
        it[IssueKeyAliasesTable.projectIdentifier] = project.identifier
        it[IssueKeyAliasesTable.sequenceNo] = sequence
        it[IssueKeyAliasesTable.isCurrent] = true
        it[IssueKeyAliasesTable.createdAt] = now
        it[IssueKeyAliasesTable.createdBy] = command.actorUserId.toKotlinUuid()
      }
      insertPropertyValues(command.tenantId, issueId, propertyValues, command.actorUserId, now)
      insertStatusHistory(
        tenantId = command.tenantId,
        issueId = issueId,
        fromStatusId = null,
        toStatusId = initialStatusId,
        transitionId = null,
        actorUserId = command.actorUserId,
        changedAt = now,
      )
      WorkItemMutationResult(
        workItem = requireWorkItem(command.tenantId, command.projectId, issueApiId.value),
        eventType = "work_item.created",
      )
    }

  override suspend fun findByApiId(tenantId: UUID, apiId: String): WorkItemRecord? =
    suspendTransaction(db = database) {
      IssuesTable.selectAll()
        .where {
          (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssuesTable.apiId eq apiId) and
            IssuesTable.deletedAt.isNull()
        }
        .singleOrNull()
        ?.toWorkItemRecord()
    }

  override suspend fun findByApiId(
    tenantId: UUID,
    projectId: UUID,
    apiId: String,
  ): WorkItemRecord? =
    suspendTransaction(db = database) {
      IssuesTable.selectAll()
        .where {
          (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssuesTable.projectId eq projectId.toKotlinUuid()) and
            (IssuesTable.apiId eq apiId) and
            IssuesTable.deletedAt.isNull()
        }
        .singleOrNull()
        ?.toWorkItemRecord()
    }

  override suspend fun listByProject(
    tenantId: UUID,
    projectId: UUID,
    limit: Int,
    offset: Long,
  ): List<WorkItemRecord> =
    suspendTransaction(db = database) {
      IssuesTable.selectAll()
        .where {
          (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssuesTable.projectId eq projectId.toKotlinUuid()) and
            IssuesTable.deletedAt.isNull()
        }
        .orderBy(IssuesTable.updatedAt to SortOrder.DESC)
        .limit(limit)
        .offset(offset)
        .map { it.toWorkItemRecord() }
    }

  override suspend fun listPropertyValues(
    tenantId: UUID,
    issueId: UUID,
  ): Map<String, JsonElement> =
    suspendTransaction(db = database) {
      IssuePropertyValuesTable.selectAll()
        .where {
          (IssuePropertyValuesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssuePropertyValuesTable.issueId eq issueId.toKotlinUuid())
        }
        .associate { row ->
          val propertyId = row[IssuePropertyValuesTable.propertyId].toJavaUuid()
          propertyCode(propertyId) to row.toJsonValue()
        }
    }

  override suspend fun update(
    command: UpdateWorkItemCommand,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult =
    suspendTransaction(db = database) {
      val issue = requireIssueRow(command.tenantId, command.projectId, command.workItemApiId)
      val issueId = issue[IssuesTable.id].toJavaUuid()
      val current = issue[IssuesTable.propertiesSnapshot].asObject()
      val snapshot = JsonObject(current + snapshot(propertyValues))
      val now = now()
      IssuesTable.update({
        (IssuesTable.tenantId eq command.tenantId.toKotlinUuid()) and
          (IssuesTable.id eq issue[IssuesTable.id])
      }) {
        command.title?.let { value -> it[IssuesTable.title] = value }
        command.description?.let { value -> it[IssuesTable.description] = value }
        command.assigneeApiId?.let { value ->
          it[IssuesTable.assigneeId] = resolveUser(value).toKotlinUuid()
        }
        command.priorityApiId?.let { value ->
          it[IssuesTable.priorityId] = resolvePriority(command.tenantId, value).toKotlinUuid()
        }
        command.sprintApiId?.let { value ->
          it[IssuesTable.sprintId] =
            resolveSprint(command.tenantId, command.projectId, value).toKotlinUuid()
        }
        it[IssuesTable.propertiesSnapshot] = snapshot
        it[IssuesTable.updatedBy] = command.actorUserId.toKotlinUuid()
        it[IssuesTable.updatedAt] = now
      }
      replacePropertyValues(command.tenantId, issueId, propertyValues, command.actorUserId, now)
      WorkItemMutationResult(
        workItem = requireWorkItem(command.tenantId, command.projectId, command.workItemApiId),
        eventType = "work_item.updated",
      )
    }

  override suspend fun transition(
    command: TransitionWorkItemCommand,
    fromStatusId: UUID,
    toStatusId: UUID,
    transitionId: UUID,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult =
    suspendTransaction(db = database) {
      val issue = requireIssueRow(command.tenantId, command.projectId, command.workItemApiId)
      val issueId = issue[IssuesTable.id].toJavaUuid()
      val current = issue[IssuesTable.propertiesSnapshot].asObject()
      val snapshot = JsonObject(current + snapshot(propertyValues))
      val now = now()
      IssuesTable.update({
        (IssuesTable.tenantId eq command.tenantId.toKotlinUuid()) and
          (IssuesTable.id eq issue[IssuesTable.id]) and
          (IssuesTable.statusId eq fromStatusId.toKotlinUuid())
      }) {
        it[IssuesTable.statusId] = toStatusId.toKotlinUuid()
        command.title?.let { value -> it[IssuesTable.title] = value }
        command.description?.let { value -> it[IssuesTable.description] = value }
        command.assigneeApiId?.let { value ->
          it[IssuesTable.assigneeId] = resolveUser(value).toKotlinUuid()
        }
        command.priorityApiId?.let { value ->
          it[IssuesTable.priorityId] = resolvePriority(command.tenantId, value).toKotlinUuid()
        }
        command.sprintApiId?.let { value ->
          it[IssuesTable.sprintId] =
            resolveSprint(command.tenantId, command.projectId, value).toKotlinUuid()
        }
        it[IssuesTable.propertiesSnapshot] = snapshot
        it[IssuesTable.updatedBy] = command.actorUserId.toKotlinUuid()
        it[IssuesTable.updatedAt] = now
      }
      replacePropertyValues(command.tenantId, issueId, propertyValues, command.actorUserId, now)
      insertStatusHistory(
        tenantId = command.tenantId,
        issueId = issueId,
        fromStatusId = fromStatusId,
        toStatusId = toStatusId,
        transitionId = transitionId,
        actorUserId = command.actorUserId,
        changedAt = now,
      )
      WorkItemMutationResult(
        workItem = requireWorkItem(command.tenantId, command.projectId, command.workItemApiId),
        eventType = "work_item.transitioned",
      )
    }

  override suspend fun countChildrenNotInStatusGroups(
    tenantId: UUID,
    issueId: UUID,
    terminalGroups: Set<String>,
  ): Long =
    suspendTransaction(db = database) {
      val childIds =
        IssueHierarchyTable.selectAll()
          .where {
            (IssueHierarchyTable.tenantId eq tenantId.toKotlinUuid()) and
              (IssueHierarchyTable.parentIssueId eq issueId.toKotlinUuid())
          }
          .map { it[IssueHierarchyTable.childIssueId] }
      if (childIds.isEmpty()) {
        return@suspendTransaction 0
      }
      IssuesTable.selectAll()
        .where {
          (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and (IssuesTable.id inList childIds)
        }
        .count { row ->
          requireStatus(row[IssuesTable.statusId].toJavaUuid()).group !in terminalGroups
        }
        .toLong()
    }

  override suspend fun resolveUserApiId(userId: UUID): PublicId? =
    suspendTransaction(db = database) {
      UsersTable.selectAll()
        .where { UsersTable.id eq userId.toKotlinUuid() }
        .singleOrNull()
        ?.get(UsersTable.apiId)
        ?.let(::PublicId)
    }

  override suspend fun resolveProjectApiId(tenantId: UUID, projectId: UUID): PublicId? =
    suspendTransaction(db = database) {
      ProjectsTable.selectAll()
        .where {
          (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and
            (ProjectsTable.id eq projectId.toKotlinUuid())
        }
        .singleOrNull()
        ?.get(ProjectsTable.apiId)
        ?.let(::PublicId)
    }

  private fun allocateSequence(projectId: UUID): Long {
    val current =
      ProjectsTable.selectAll()
        .where { ProjectsTable.id eq projectId.toKotlinUuid() }
        .single()[ProjectsTable.nextIssueSequence]
    ProjectsTable.update({ ProjectsTable.id eq projectId.toKotlinUuid() }) {
      it[ProjectsTable.nextIssueSequence] = current + 1
    }
    return current
  }

  private fun requireIssueRow(tenantId: UUID, projectId: UUID, apiId: String): ResultRow =
    IssuesTable.selectAll()
      .where {
        (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and
          (IssuesTable.projectId eq projectId.toKotlinUuid()) and
          (IssuesTable.apiId eq apiId) and
          IssuesTable.deletedAt.isNull()
      }
      .singleOrNull()
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

  private fun requireWorkItem(tenantId: UUID, projectId: UUID, apiId: String): WorkItemRecord =
    requireIssueRow(tenantId, projectId, apiId).toWorkItemRecord()

  private fun requireProject(tenantId: UUID, projectId: UUID): ProjectRow =
    ProjectsTable.selectAll()
      .where {
        (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and
          (ProjectsTable.id eq projectId.toKotlinUuid())
      }
      .singleOrNull()
      ?.let {
        ProjectRow(
          id = it[ProjectsTable.id].toJavaUuid(),
          identifier = it[ProjectsTable.identifier],
        )
      } ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)

  private fun replacePropertyValues(
    tenantId: UUID,
    issueId: UUID,
    values: List<WorkItemPropertyValue>,
    actorUserId: UUID,
    now: OffsetDateTime,
  ) {
    if (values.isEmpty()) return
    val propertyIds = values.map { it.propertyId.toKotlinUuid() }
    IssuePropertyValuesTable.deleteWhere {
      (IssuePropertyValuesTable.tenantId eq tenantId.toKotlinUuid()) and
        (IssuePropertyValuesTable.issueId eq issueId.toKotlinUuid()) and
        (IssuePropertyValuesTable.propertyId inList propertyIds)
    }
    insertPropertyValues(tenantId, issueId, values, actorUserId, now)
  }

  private fun insertPropertyValues(
    tenantId: UUID,
    issueId: UUID,
    values: List<WorkItemPropertyValue>,
    actorUserId: UUID,
    now: OffsetDateTime,
  ) {
    values.forEach { value ->
      IssuePropertyValuesTable.insert {
        it[IssuePropertyValuesTable.id] = UUID.randomUUID().toKotlinUuid()
        it[IssuePropertyValuesTable.tenantId] = tenantId.toKotlinUuid()
        it[IssuePropertyValuesTable.issueId] = issueId.toKotlinUuid()
        it[IssuePropertyValuesTable.propertyId] = value.propertyId.toKotlinUuid()
        writePropertyColumns(it, tenantId, value)
        it[IssuePropertyValuesTable.updatedBy] = actorUserId.toKotlinUuid()
        it[IssuePropertyValuesTable.createdAt] = now
        it[IssuePropertyValuesTable.updatedAt] = now
      }
    }
  }

  @Suppress("CyclomaticComplexMethod")
  private fun writePropertyColumns(
    row: org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>,
    tenantId: UUID,
    value: WorkItemPropertyValue,
  ) {
    val primitive = value.value as? JsonPrimitive
    when (value.dataType) {
      WorkItemPropertyDataType.TEXT,
      WorkItemPropertyDataType.LONG_TEXT,
      WorkItemPropertyDataType.URL -> row[IssuePropertyValuesTable.valueText] = primitive?.content
      WorkItemPropertyDataType.NUMBER ->
        row[IssuePropertyValuesTable.valueNumber] = primitive?.content?.let(::BigDecimal)
      WorkItemPropertyDataType.BOOLEAN ->
        row[IssuePropertyValuesTable.valueBoolean] = primitive?.content?.toBooleanStrictOrNull()
      WorkItemPropertyDataType.DATE -> row[IssuePropertyValuesTable.valueDate] = primitive?.content
      WorkItemPropertyDataType.DATETIME ->
        row[IssuePropertyValuesTable.valueDatetime] = primitive?.content?.let(OffsetDateTime::parse)
      WorkItemPropertyDataType.JSON -> row[IssuePropertyValuesTable.valueJson] = value.value
      WorkItemPropertyDataType.USER ->
        row[IssuePropertyValuesTable.valueUserId] =
          primitive?.content?.let(::resolveUser)?.toKotlinUuid()
      WorkItemPropertyDataType.PROJECT ->
        row[IssuePropertyValuesTable.valueProjectId] =
          primitive?.content?.let { resolveProject(tenantId, it) }?.toKotlinUuid()
      WorkItemPropertyDataType.ISSUE ->
        row[IssuePropertyValuesTable.valueIssueId] =
          primitive?.content?.let { resolveIssue(tenantId, it) }?.toKotlinUuid()
      WorkItemPropertyDataType.SINGLE_SELECT ->
        row[IssuePropertyValuesTable.valueOptionId] =
          primitive?.content?.let { resolveOption(value.propertyId, it) }?.toKotlinUuid()
      WorkItemPropertyDataType.MULTI_SELECT,
      WorkItemPropertyDataType.MULTI_USER -> row[IssuePropertyValuesTable.valueArray] = value.value
    }
  }

  private fun insertStatusHistory(
    tenantId: UUID,
    issueId: UUID,
    fromStatusId: UUID?,
    toStatusId: UUID,
    transitionId: UUID?,
    actorUserId: UUID,
    changedAt: OffsetDateTime,
  ) {
    IssueStatusHistoryTable.insert {
      it[IssueStatusHistoryTable.id] = UUID.randomUUID().toKotlinUuid()
      it[IssueStatusHistoryTable.tenantId] = tenantId.toKotlinUuid()
      it[IssueStatusHistoryTable.issueId] = issueId.toKotlinUuid()
      it[IssueStatusHistoryTable.fromStatusId] = fromStatusId?.toKotlinUuid()
      it[IssueStatusHistoryTable.toStatusId] = toStatusId.toKotlinUuid()
      it[IssueStatusHistoryTable.transitionId] = transitionId?.toKotlinUuid()
      it[IssueStatusHistoryTable.actorUserId] = actorUserId.toKotlinUuid()
      it[IssueStatusHistoryTable.changedAt] = changedAt
      it[IssueStatusHistoryTable.metadata] = JsonObject(emptyMap())
    }
  }

  private fun ResultRow.toWorkItemRecord(): WorkItemRecord {
    val issueType = requirePublicId(IssueTypesTable, this[IssuesTable.issueTypeId].toJavaUuid())
    val config =
      requirePublicId(IssueTypeConfigsTable, this[IssuesTable.issueTypeConfigId].toJavaUuid())
    val status = requireStatus(this[IssuesTable.statusId].toJavaUuid())
    return WorkItemRecord(
      id = this[IssuesTable.id].toJavaUuid(),
      apiId = PublicId(this[IssuesTable.apiId]),
      tenantId = this[IssuesTable.tenantId].toJavaUuid(),
      projectId = this[IssuesTable.projectId].toJavaUuid(),
      issueTypeApiId = issueType,
      issueTypeConfigApiId = config,
      key = currentKey(this[IssuesTable.id].toJavaUuid()) ?: fallbackKey(this),
      title = this[IssuesTable.title],
      description = this[IssuesTable.description],
      statusId = this[IssuesTable.statusId].toJavaUuid(),
      statusApiId = status.apiId,
      statusGroup = WorkItemStatusGroup.fromDbValue(status.group),
      reporterId = this[IssuesTable.reporterId].toJavaUuid(),
      assigneeId = this[IssuesTable.assigneeId]?.toJavaUuid(),
      priorityApiId =
        this[IssuesTable.priorityId]?.toJavaUuid()?.let { requirePublicId(PrioritiesTable, it) },
      reporterApiId = requirePublicId(UsersTable, this[IssuesTable.reporterId].toJavaUuid()),
      assigneeApiId =
        this[IssuesTable.assigneeId]?.toJavaUuid()?.let { requirePublicId(UsersTable, it) },
      sprintApiId =
        this[IssuesTable.sprintId]?.toJavaUuid()?.let { requirePublicId(SprintsTable, it) },
      properties = this[IssuesTable.propertiesSnapshot].asObject(),
      createdAt = this[IssuesTable.createdAt],
      updatedAt = this[IssuesTable.updatedAt],
    )
  }

  @Suppress("CyclomaticComplexMethod")
  private fun ResultRow.toJsonValue(): JsonElement =
    this[IssuePropertyValuesTable.valueText]?.let(::JsonPrimitive)
      ?: this[IssuePropertyValuesTable.valueNumber]?.let { JsonPrimitive(it) }
      ?: this[IssuePropertyValuesTable.valueBoolean]?.let { JsonPrimitive(it) }
      ?: this[IssuePropertyValuesTable.valueDate]?.let(::JsonPrimitive)
      ?: this[IssuePropertyValuesTable.valueDatetime]?.let { JsonPrimitive(it.toString()) }
      ?: this[IssuePropertyValuesTable.valueJson]
      ?: this[IssuePropertyValuesTable.valueUserId]?.let {
        JsonPrimitive(requirePublicId(UsersTable, it.toJavaUuid()).value)
      }
      ?: this[IssuePropertyValuesTable.valueProjectId]?.let {
        JsonPrimitive(requirePublicId(ProjectsTable, it.toJavaUuid()).value)
      }
      ?: this[IssuePropertyValuesTable.valueIssueId]?.let {
        JsonPrimitive(requirePublicId(IssuesTable, it.toJavaUuid()).value)
      }
      ?: this[IssuePropertyValuesTable.valueOptionId]?.let {
        JsonPrimitive(requirePublicId(PropertyOptionsTable, it.toJavaUuid()).value)
      }
      ?: this[IssuePropertyValuesTable.valueArray]
      ?: JsonNull

  private fun snapshot(values: List<WorkItemPropertyValue>): JsonObject =
    JsonObject(values.associate { it.code to it.value })

  private fun JsonElement.asObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

  private fun currentKey(issueId: UUID): String? =
    IssueKeyAliasesTable.selectAll()
      .where {
        (IssueKeyAliasesTable.issueId eq issueId.toKotlinUuid()) and
          (IssueKeyAliasesTable.isCurrent eq true)
      }
      .singleOrNull()
      ?.get(IssueKeyAliasesTable.issueKey)

  private fun fallbackKey(row: ResultRow): String {
    val identifier =
      ProjectsTable.selectAll()
        .where { ProjectsTable.id eq row[IssuesTable.projectId] }
        .single()[ProjectsTable.identifier]
    return "$identifier-${row[IssuesTable.sequenceNo]}"
  }

  private fun propertyCode(propertyId: UUID): String =
    ink.doa.workbench.data.persistence.PropertyDefinitionsTable.selectAll()
      .where {
        ink.doa.workbench.data.persistence.PropertyDefinitionsTable.id eq propertyId.toKotlinUuid()
      }
      .single()[ink.doa.workbench.data.persistence.PropertyDefinitionsTable.code]

  private fun requireStatus(statusId: UUID): StatusRow =
    IssueStatusesTable.selectAll()
      .where { IssueStatusesTable.id eq statusId.toKotlinUuid() }
      .single()
      .let { StatusRow(PublicId(it[IssueStatusesTable.apiId]), it[IssueStatusesTable.statusGroup]) }

  private fun resolveUser(apiId: String): UUID =
    UsersTable.selectAll()
      .where { UsersTable.apiId eq apiId }
      .singleOrNull()
      ?.get(UsersTable.id)
      ?.toJavaUuid() ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  private fun resolvePriority(tenantId: UUID, apiIdOrCode: String): UUID =
    PrioritiesTable.selectAll()
      .where {
        (PrioritiesTable.tenantId eq tenantId.toKotlinUuid()) and
          ((PrioritiesTable.apiId eq apiIdOrCode) or (PrioritiesTable.code eq apiIdOrCode))
      }
      .singleOrNull()
      ?.get(PrioritiesTable.id)
      ?.toJavaUuid() ?: throw ResourceNotFoundException(WorkbenchErrorCode.REQUEST_INVALID)

  private fun resolveSprint(tenantId: UUID, projectId: UUID, apiId: String): UUID =
    SprintsTable.selectAll()
      .where {
        (SprintsTable.tenantId eq tenantId.toKotlinUuid()) and
          (SprintsTable.projectId eq projectId.toKotlinUuid()) and
          (SprintsTable.apiId eq apiId)
      }
      .singleOrNull()
      ?.get(SprintsTable.id)
      ?.toJavaUuid() ?: throw ResourceNotFoundException(WorkbenchErrorCode.REQUEST_INVALID)

  private fun resolveProject(tenantId: UUID, apiId: String): UUID =
    ProjectsTable.selectAll()
      .where {
        (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and (ProjectsTable.apiId eq apiId)
      }
      .singleOrNull()
      ?.get(ProjectsTable.id)
      ?.toJavaUuid()
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)

  private fun resolveIssue(tenantId: UUID, apiId: String): UUID =
    IssuesTable.selectAll()
      .where {
        (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and (IssuesTable.apiId eq apiId)
      }
      .singleOrNull()
      ?.get(IssuesTable.id)
      ?.toJavaUuid()
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

  private fun resolveOption(propertyId: UUID, apiIdOrCode: String): UUID =
    PropertyOptionsTable.selectAll()
      .where {
        (PropertyOptionsTable.propertyId eq propertyId.toKotlinUuid()) and
          ((PropertyOptionsTable.apiId eq apiIdOrCode) or
            (PropertyOptionsTable.code eq apiIdOrCode))
      }
      .singleOrNull()
      ?.get(PropertyOptionsTable.id)
      ?.toJavaUuid()
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_PROPERTY_NOT_FOUND)

  private fun requirePublicId(table: org.jetbrains.exposed.v1.core.Table, id: UUID): PublicId {
    val apiIdColumn =
      table.columns.single { it.name == "api_id" } as org.jetbrains.exposed.v1.core.Column<String>
    val idColumn =
      table.columns.single { it.name == "id" }
        as org.jetbrains.exposed.v1.core.Column<kotlin.uuid.Uuid>
    return PublicId(table.selectAll().where { idColumn eq id.toKotlinUuid() }.single()[apiIdColumn])
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

  private data class ProjectRow(val id: UUID, val identifier: String)

  private data class StatusRow(val apiId: PublicId, val group: String)
}

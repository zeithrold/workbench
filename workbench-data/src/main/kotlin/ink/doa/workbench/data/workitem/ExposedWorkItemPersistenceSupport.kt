package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.data.persistence.IssueKeyAliasesTable
import ink.doa.workbench.data.persistence.IssueStatusesTable
import ink.doa.workbench.data.persistence.IssuesTable
import ink.doa.workbench.data.persistence.ProjectsTable
import ink.doa.workbench.data.persistence.PropertyDefinitionsTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

internal data class ProjectRow(val id: UUID, val identifier: String)

internal data class StatusRow(val apiId: PublicId, val group: String)

internal fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

internal fun JsonElement.asObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

internal fun snapshot(values: List<WorkItemPropertyValue>): JsonObject =
  JsonObject(values.associate { it.code to it.value })

internal fun requireIssueRow(tenantId: UUID, projectId: UUID, apiId: String): ResultRow =
  IssuesTable.selectAll()
    .where {
      (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and
        (IssuesTable.projectId eq projectId.toKotlinUuid()) and
        (IssuesTable.apiId eq apiId) and
        IssuesTable.archivedAt.isNull() and
        IssuesTable.deletedAt.isNull()
    }
    .singleOrNull()
    ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

internal fun requireProject(tenantId: UUID, projectId: UUID): ProjectRow =
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

internal fun allocateSequence(projectId: UUID): Long {
  val current =
    ProjectsTable.selectAll()
      .where { ProjectsTable.id eq projectId.toKotlinUuid() }
      .single()[ProjectsTable.nextIssueSequence]
  ProjectsTable.update({ ProjectsTable.id eq projectId.toKotlinUuid() }) {
    it[ProjectsTable.nextIssueSequence] = current + 1
  }
  return current
}

internal fun requirePublicId(table: org.jetbrains.exposed.v1.core.Table, id: UUID): PublicId {
  val apiIdColumn =
    table.columns.single { it.name == "api_id" } as org.jetbrains.exposed.v1.core.Column<String>
  val idColumn =
    table.columns.single { it.name == "id" }
      as org.jetbrains.exposed.v1.core.Column<kotlin.uuid.Uuid>
  return PublicId(table.selectAll().where { idColumn eq id.toKotlinUuid() }.single()[apiIdColumn])
}

internal fun requireStatus(statusId: UUID): StatusRow =
  IssueStatusesTable.selectAll()
    .where { IssueStatusesTable.id eq statusId.toKotlinUuid() }
    .single()
    .let { StatusRow(PublicId(it[IssueStatusesTable.apiId]), it[IssueStatusesTable.statusGroup]) }

internal fun propertyCode(propertyId: UUID): String =
  PropertyDefinitionsTable.selectAll()
    .where { PropertyDefinitionsTable.id eq propertyId.toKotlinUuid() }
    .single()[PropertyDefinitionsTable.code]

internal fun currentKey(issueId: UUID): String? =
  IssueKeyAliasesTable.selectAll()
    .where {
      (IssueKeyAliasesTable.issueId eq issueId.toKotlinUuid()) and
        (IssueKeyAliasesTable.isCurrent eq true)
    }
    .singleOrNull()
    ?.get(IssueKeyAliasesTable.issueKey)

internal fun fallbackKey(row: ResultRow): String {
  val identifier =
    ProjectsTable.selectAll()
      .where { ProjectsTable.id eq row[IssuesTable.projectId] }
      .single()[ProjectsTable.identifier]
  return "$identifier-${row[IssuesTable.sequenceNo]}"
}

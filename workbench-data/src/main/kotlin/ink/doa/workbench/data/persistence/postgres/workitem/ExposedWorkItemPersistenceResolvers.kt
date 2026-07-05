package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.data.persistence.IssuesTable
import ink.doa.workbench.data.persistence.PrioritiesTable
import ink.doa.workbench.data.persistence.ProjectsTable
import ink.doa.workbench.data.persistence.PropertyOptionsTable
import ink.doa.workbench.data.persistence.SprintsTable
import ink.doa.workbench.data.persistence.UsersTable
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll

internal fun resolveUser(apiId: String): UUID =
  UsersTable.selectAll()
    .where { UsersTable.apiId eq apiId }
    .singleOrNull()
    ?.get(UsersTable.id)
    ?.toJavaUuid() ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

internal fun resolvePriority(tenantId: UUID, apiIdOrCode: String): UUID =
  PrioritiesTable.selectAll()
    .where {
      (PrioritiesTable.tenantId eq tenantId.toKotlinUuid()) and
        ((PrioritiesTable.apiId eq apiIdOrCode) or (PrioritiesTable.code eq apiIdOrCode))
    }
    .singleOrNull()
    ?.get(PrioritiesTable.id)
    ?.toJavaUuid() ?: throw ResourceNotFoundException(WorkbenchErrorCode.REQUEST_INVALID)

internal fun resolveSprint(tenantId: UUID, projectId: UUID, apiId: String): UUID =
  SprintsTable.selectAll()
    .where {
      (SprintsTable.tenantId eq tenantId.toKotlinUuid()) and
        (SprintsTable.projectId eq projectId.toKotlinUuid()) and
        (SprintsTable.apiId eq apiId)
    }
    .singleOrNull()
    ?.get(SprintsTable.id)
    ?.toJavaUuid() ?: throw ResourceNotFoundException(WorkbenchErrorCode.REQUEST_INVALID)

internal fun resolveProject(tenantId: UUID, apiId: String): UUID =
  ProjectsTable.selectAll()
    .where {
      (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and (ProjectsTable.apiId eq apiId)
    }
    .singleOrNull()
    ?.get(ProjectsTable.id)
    ?.toJavaUuid() ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)

internal fun resolveIssue(tenantId: UUID, apiId: String): UUID =
  IssuesTable.selectAll()
    .where {
      (IssuesTable.tenantId eq tenantId.toKotlinUuid()) and (IssuesTable.apiId eq apiId)
    }
    .singleOrNull()
    ?.get(IssuesTable.id)
    ?.toJavaUuid()
    ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

internal fun resolveOption(propertyId: UUID, apiIdOrCode: String): UUID =
  PropertyOptionsTable.selectAll()
    .where {
      (PropertyOptionsTable.propertyId eq propertyId.toKotlinUuid()) and
        ((PropertyOptionsTable.apiId eq apiIdOrCode) or (PropertyOptionsTable.code eq apiIdOrCode))
    }
    .singleOrNull()
    ?.get(PropertyOptionsTable.id)
    ?.toJavaUuid()
    ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_PROPERTY_NOT_FOUND)

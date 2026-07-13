package ink.doa.workbench.data.repository.sprint

import ink.doa.workbench.agile.sprint.model.SprintRecord
import ink.doa.workbench.agile.sprint.model.SprintStatus
import ink.doa.workbench.data.persistence.postgres.workitem.SprintsTable
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.common.ids.PublicId
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll

internal fun activeSprintScope(
  tenantId: UUID,
  projectId: UUID,
  status: SprintStatus? = null,
): Op<Boolean> {
  var condition =
    (SprintsTable.tenantId eq tenantId.toKotlinUuid()) and
      (SprintsTable.projectId eq projectId.toKotlinUuid()) and
      SprintsTable.deletedAt.isNull() and
      SprintsTable.archivedAt.isNull()
  if (status != null) {
    condition = condition and (SprintsTable.status eq status.dbValue)
  }
  return condition
}

internal fun requireSprintRow(tenantId: UUID, projectId: UUID, sprintApiId: String): ResultRow =
  SprintsTable.selectAll()
    .where { activeSprintScope(tenantId, projectId) and (SprintsTable.apiId eq sprintApiId) }
    .singleOrNull() ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_SPRINT_NOT_FOUND)

internal fun ResultRow.toSprintRecord(): SprintRecord =
  SprintRecord(
    id = this[SprintsTable.id].toJavaUuid(),
    apiId = PublicId(this[SprintsTable.apiId]),
    tenantId = this[SprintsTable.tenantId].toJavaUuid(),
    projectId = this[SprintsTable.projectId].toJavaUuid(),
    name = this[SprintsTable.name],
    goal = this[SprintsTable.goal],
    status = SprintStatus.fromDbValue(this[SprintsTable.status]),
    startAt = this[SprintsTable.startAt],
    endAt = this[SprintsTable.endAt],
    closedAt = this[SprintsTable.closedAt],
    createdBy = this[SprintsTable.createdBy]?.toJavaUuid(),
    archivedAt = this[SprintsTable.archivedAt],
    archivedBy = this[SprintsTable.archivedBy]?.toJavaUuid(),
    deletedAt = this[SprintsTable.deletedAt],
    createdAt = this[SprintsTable.createdAt],
    updatedAt = this[SprintsTable.updatedAt],
  )

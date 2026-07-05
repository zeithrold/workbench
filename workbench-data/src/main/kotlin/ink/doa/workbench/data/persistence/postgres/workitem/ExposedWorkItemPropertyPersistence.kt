package ink.doa.workbench.data.workitem

import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.data.persistence.IssuePropertyValuesTable
import ink.doa.workbench.data.persistence.IssueStatusHistoryTable
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert

internal fun replacePropertyValues(
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

internal fun insertPropertyValues(
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

data class StatusHistoryEntry(
  val tenantId: UUID,
  val issueId: UUID,
  val fromStatusId: UUID?,
  val toStatusId: UUID,
  val transitionId: UUID?,
  val actorUserId: UUID,
  val changedAt: OffsetDateTime,
)

internal fun insertStatusHistory(entry: StatusHistoryEntry): UUID {
  val historyId = UUID.randomUUID()
  IssueStatusHistoryTable.insert {
    it[IssueStatusHistoryTable.id] = historyId.toKotlinUuid()
    it[IssueStatusHistoryTable.tenantId] = entry.tenantId.toKotlinUuid()
    it[IssueStatusHistoryTable.issueId] = entry.issueId.toKotlinUuid()
    it[IssueStatusHistoryTable.fromStatusId] = entry.fromStatusId?.toKotlinUuid()
    it[IssueStatusHistoryTable.toStatusId] = entry.toStatusId.toKotlinUuid()
    it[IssueStatusHistoryTable.transitionId] = entry.transitionId?.toKotlinUuid()
    it[IssueStatusHistoryTable.actorUserId] = entry.actorUserId.toKotlinUuid()
    it[IssueStatusHistoryTable.changedAt] = entry.changedAt
    it[IssueStatusHistoryTable.metadata] = JsonObject(emptyMap())
  }
  return historyId
}

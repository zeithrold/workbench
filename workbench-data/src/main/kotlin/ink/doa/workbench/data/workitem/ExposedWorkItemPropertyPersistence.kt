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

internal fun insertStatusHistory(
  tenantId: UUID,
  issueId: UUID,
  fromStatusId: UUID?,
  toStatusId: UUID,
  transitionId: UUID?,
  actorUserId: UUID,
  changedAt: OffsetDateTime,
): UUID {
  val historyId = UUID.randomUUID()
  IssueStatusHistoryTable.insert {
    it[IssueStatusHistoryTable.id] = historyId.toKotlinUuid()
    it[IssueStatusHistoryTable.tenantId] = tenantId.toKotlinUuid()
    it[IssueStatusHistoryTable.issueId] = issueId.toKotlinUuid()
    it[IssueStatusHistoryTable.fromStatusId] = fromStatusId?.toKotlinUuid()
    it[IssueStatusHistoryTable.toStatusId] = toStatusId.toKotlinUuid()
    it[IssueStatusHistoryTable.transitionId] = transitionId?.toKotlinUuid()
    it[IssueStatusHistoryTable.actorUserId] = actorUserId.toKotlinUuid()
    it[IssueStatusHistoryTable.changedAt] = changedAt
    it[IssueStatusHistoryTable.metadata] = JsonObject(emptyMap())
  }
  return historyId
}

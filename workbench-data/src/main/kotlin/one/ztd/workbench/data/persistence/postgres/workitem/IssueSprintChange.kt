package one.ztd.workbench.data.persistence.postgres.workitem

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

internal data class IssueSprintChange(
  val tenantId: UUID,
  val issueId: UUID,
  val previousSprintId: UUID?,
  val nextSprintId: UUID?,
  val actorUserId: UUID,
  val changedAt: OffsetDateTime,
)

internal fun recordIssueSprintChange(change: IssueSprintChange) {
  if (change.previousSprintId == change.nextSprintId) return
  change.previousSprintId?.let { sprintId ->
    closeOpenSprintHistory(
      tenantId = change.tenantId,
      issueId = change.issueId,
      sprintId = sprintId,
      actorUserId = change.actorUserId,
      removedAt = change.changedAt,
    )
  }
  change.nextSprintId?.let { sprintId ->
    IssueSprintHistoryTable.insert {
      it[IssueSprintHistoryTable.id] = UUID.randomUUID().toKotlinUuid()
      it[IssueSprintHistoryTable.tenantId] = change.tenantId.toKotlinUuid()
      it[IssueSprintHistoryTable.issueId] = change.issueId.toKotlinUuid()
      it[IssueSprintHistoryTable.sprintId] = sprintId.toKotlinUuid()
      it[IssueSprintHistoryTable.addedBy] = change.actorUserId.toKotlinUuid()
      it[IssueSprintHistoryTable.addedAt] = change.changedAt
    }
  }
}

private fun closeOpenSprintHistory(
  tenantId: UUID,
  issueId: UUID,
  sprintId: UUID,
  actorUserId: UUID,
  removedAt: OffsetDateTime,
) {
  val openRow =
    IssueSprintHistoryTable.selectAll()
      .where {
        (IssueSprintHistoryTable.tenantId eq tenantId.toKotlinUuid()) and
          (IssueSprintHistoryTable.issueId eq issueId.toKotlinUuid()) and
          (IssueSprintHistoryTable.sprintId eq sprintId.toKotlinUuid()) and
          IssueSprintHistoryTable.removedAt.isNull()
      }
      .singleOrNull() ?: return
  IssueSprintHistoryTable.update({
    IssueSprintHistoryTable.id eq openRow[IssueSprintHistoryTable.id]
  }) {
    it[IssueSprintHistoryTable.removedBy] = actorUserId.toKotlinUuid()
    it[IssueSprintHistoryTable.removedAt] = removedAt
  }
}

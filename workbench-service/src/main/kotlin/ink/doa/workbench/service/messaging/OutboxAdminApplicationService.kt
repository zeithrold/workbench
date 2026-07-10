package ink.doa.workbench.service.messaging

import ink.doa.workbench.core.common.errors.ResourceConflictException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.messaging.OutboxMessageQuery
import ink.doa.workbench.core.messaging.OutboxMessageRecord
import ink.doa.workbench.core.port.messaging.OutboxAdminStore
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class OutboxAdminApplicationService(private val store: OutboxAdminStore) {
  fun list(
    status: String?,
    tenantId: String?,
    eventType: String?,
    limit: Int,
    offset: Long,
  ): List<OutboxMessageRecord> =
    store.list(
      OutboxMessageQuery(
        status = status,
        tenantId = tenantId,
        eventType = eventType,
        limit = limit,
        offset = offset,
      )
    )

  fun get(id: UUID): OutboxMessageRecord =
    store.findById(id)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.OUTBOX_MESSAGE_NOT_FOUND)

  fun replay(id: UUID): OutboxMessageRecord {
    val existing = get(id)
    if (existing.status != "DEAD") {
      throw ResourceConflictException(WorkbenchErrorCode.OUTBOX_REPLAY_NOT_ALLOWED)
    }
    if (!store.replayDead(id)) {
      throw ResourceConflictException(WorkbenchErrorCode.OUTBOX_REPLAY_NOT_ALLOWED)
    }
    return get(id)
  }
}

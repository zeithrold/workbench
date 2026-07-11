package ink.doa.workbench.service.messaging

import ink.doa.workbench.core.common.errors.ResourceConflictException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.messaging.OutboxDeliveryQuery
import ink.doa.workbench.core.messaging.OutboxDeliveryRecord
import ink.doa.workbench.core.port.messaging.OutboxDeliveryAdminStore
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class OutboxDeliveryAdminApplicationService(private val store: OutboxDeliveryAdminStore) {
  fun list(query: OutboxDeliveryQuery): List<OutboxDeliveryRecord> = store.listDeliveries(query)

  fun replay(outboxId: UUID, consumerName: String): OutboxDeliveryRecord {
    if (!store.replayDeadDelivery(outboxId, consumerName)) {
      throw ResourceConflictException(WorkbenchErrorCode.OUTBOX_REPLAY_NOT_ALLOWED)
    }
    return store
      .listDeliveries(OutboxDeliveryQuery(consumerName = consumerName, limit = 100))
      .first { it.outboxId == outboxId }
  }
}

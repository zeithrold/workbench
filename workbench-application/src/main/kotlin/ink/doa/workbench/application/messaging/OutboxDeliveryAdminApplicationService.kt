package ink.doa.workbench.application.messaging

import ink.doa.workbench.kernel.common.errors.ResourceConflictException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class OutboxDeliveryAdminApplicationService(private val store: OutboxDeliveryAdminStore) {
  fun list(query: OutboxDeliveryQuery): List<OutboxDeliveryRecord> = store.listDeliveries(query)

  fun replay(outboxId: UUID, consumerName: String): OutboxDeliveryRecord {
    if (!store.replayDeadDelivery(outboxId, consumerName)) {
      throw ResourceConflictException(WorkbenchErrorCode.OUTBOX_DELIVERY_REPLAY_NOT_ALLOWED)
    }
    return store
      .listDeliveries(OutboxDeliveryQuery(consumerName = consumerName, limit = 100))
      .first { it.outboxId == outboxId }
  }
}

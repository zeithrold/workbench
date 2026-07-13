package ink.doa.workbench.application.messaging

import java.util.UUID

interface OutboxDeliveryAdminStore {
  fun listDeliveries(query: OutboxDeliveryQuery): List<OutboxDeliveryRecord>

  fun replayDeadDelivery(outboxId: UUID, consumerName: String): Boolean
}

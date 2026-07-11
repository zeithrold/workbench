package ink.doa.workbench.core.port.messaging

import ink.doa.workbench.core.messaging.OutboxDeliveryQuery
import ink.doa.workbench.core.messaging.OutboxDeliveryRecord
import java.util.UUID

interface OutboxDeliveryAdminStore {
  fun listDeliveries(query: OutboxDeliveryQuery): List<OutboxDeliveryRecord>

  fun replayDeadDelivery(outboxId: UUID, consumerName: String): Boolean
}

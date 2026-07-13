package ink.doa.workbench.core.port.messaging

import ink.doa.workbench.core.messaging.OutboxMessageQuery
import ink.doa.workbench.core.messaging.OutboxMessageRecord
import java.util.UUID

interface OutboxAdminStore {
  fun list(query: OutboxMessageQuery): List<OutboxMessageRecord>

  fun findById(id: UUID): OutboxMessageRecord?
}

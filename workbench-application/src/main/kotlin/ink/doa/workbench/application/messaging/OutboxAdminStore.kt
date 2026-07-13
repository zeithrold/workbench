package ink.doa.workbench.application.messaging

import java.util.UUID

interface OutboxAdminStore {
  fun list(query: OutboxMessageQuery): List<OutboxMessageRecord>

  fun findById(id: UUID): OutboxMessageRecord?
}

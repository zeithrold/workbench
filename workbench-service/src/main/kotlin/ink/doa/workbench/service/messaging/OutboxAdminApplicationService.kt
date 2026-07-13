package ink.doa.workbench.service.messaging

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.messaging.OutboxMessageQuery
import ink.doa.workbench.core.messaging.OutboxMessageRecord
import ink.doa.workbench.core.port.messaging.OutboxAdminStore
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class OutboxAdminApplicationService(private val store: OutboxAdminStore) {
  fun list(query: OutboxMessageQuery): List<OutboxMessageRecord> = store.list(query)

  fun get(id: UUID): OutboxMessageRecord =
    store.findById(id)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.OUTBOX_MESSAGE_NOT_FOUND)
}

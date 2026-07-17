package one.ztd.workbench.application.messaging

import java.util.UUID
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Service

@Service
class OutboxAdminApplicationService(private val store: OutboxAdminStore) {
  fun list(query: OutboxMessageQuery): List<OutboxMessageRecord> = store.list(query)

  fun get(id: UUID): OutboxMessageRecord =
    store.findById(id)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.OUTBOX_MESSAGE_NOT_FOUND)
}

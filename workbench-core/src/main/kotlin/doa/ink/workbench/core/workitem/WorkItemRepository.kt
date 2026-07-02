package doa.ink.workbench.core.workitem

import doa.ink.workbench.core.workitem.model.CreateWorkItemCommand
import doa.ink.workbench.core.workitem.model.WorkItemRecord
import java.util.UUID

interface WorkItemRepository {
  suspend fun create(command: CreateWorkItemCommand): WorkItemRecord

  suspend fun findByApiId(tenantId: UUID, apiId: String): WorkItemRecord?
}

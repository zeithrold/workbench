package doa.ink.workbench.workitem

import doa.ink.workbench.workitem.model.CreateWorkItemCommand
import doa.ink.workbench.workitem.model.WorkItemRecord
import java.util.UUID

interface WorkItemRepository {
  suspend fun create(command: CreateWorkItemCommand): WorkItemRecord

  suspend fun findByApiId(tenantId: UUID, apiId: String): WorkItemRecord?
}

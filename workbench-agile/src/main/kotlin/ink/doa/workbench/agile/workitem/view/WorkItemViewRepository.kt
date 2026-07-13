package ink.doa.workbench.agile.workitem.view

import java.util.UUID

interface WorkItemViewRepository {
  suspend fun listByProject(tenantId: UUID, projectId: UUID): List<WorkItemViewRecord>

  suspend fun listTenantScoped(tenantId: UUID): List<WorkItemViewRecord>

  suspend fun findByApiId(
    tenantId: UUID,
    viewApiId: String,
    projectId: UUID?,
  ): WorkItemViewRecord?

  suspend fun create(command: CreateWorkItemViewCommand): WorkItemViewRecord

  suspend fun update(command: UpdateWorkItemViewCommand): WorkItemViewRecord

  suspend fun delete(command: DeleteWorkItemViewCommand): Boolean
}

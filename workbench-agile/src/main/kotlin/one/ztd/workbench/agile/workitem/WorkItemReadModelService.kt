package one.ztd.workbench.agile.workitem

import one.ztd.workbench.agile.workitem.model.WorkItemMutationResult
import one.ztd.workbench.agile.workitem.model.WorkItemSearchHit
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Service

@Service
class WorkItemReadModelService(private val queries: WorkItemQueryRepository) {
  suspend fun get(
    tenantId: java.util.UUID,
    projectId: java.util.UUID,
    apiId: String,
  ): WorkItemSearchHit =
    queries.findByApiId(WorkItemSearchScope(tenantId = tenantId, projectId = projectId), apiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

  suspend fun afterMutation(result: WorkItemMutationResult): WorkItemSearchHit =
    get(result.workItem.tenantId, result.workItem.projectId, result.workItem.apiId.value)
}

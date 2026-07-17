package one.ztd.workbench.agile.workitem

import java.util.UUID
import one.ztd.workbench.agile.workitem.events.WorkItemDomainEvents
import one.ztd.workbench.agile.workitem.events.WorkItemMutationEvent
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.WorkItemMutationResult
import one.ztd.workbench.agile.workitem.model.WorkItemSearchHit
import one.ztd.workbench.agile.workitem.template.WorkItemValueTemplateContext
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.port.messaging.DomainEventPublisher
import org.springframework.stereotype.Component

@Component
class WorkItemMutationSupport(
  private val repository: WorkItemRepository,
  private val configs: IssueTypeConfigRepository,
  private val events: DomainEventPublisher,
  private val readModels: WorkItemReadModelService,
) {
  suspend fun read(tenantId: UUID, projectId: UUID, apiId: String): WorkItemSearchHit =
    readModels.get(tenantId, projectId, apiId)

  suspend fun present(result: WorkItemMutationResult): WorkItemSearchHit =
    readModels.afterMutation(result)

  fun publish(result: WorkItemMutationResult) {
    val spec =
      when (result.eventType) {
        WorkItemDomainEvents.Created.type -> WorkItemDomainEvents.Created
        WorkItemDomainEvents.Updated.type -> WorkItemDomainEvents.Updated
        WorkItemDomainEvents.Transitioned.type -> WorkItemDomainEvents.Transitioned
        else -> return
      }
    events.publish(
      spec = spec,
      key = result.workItem.apiId.value,
      payload = WorkItemMutationEvent.from(result.workItem),
    )
  }

  suspend fun requireConfig(tenantId: UUID, configApiId: String): IssueTypeConfigDetails =
    configs.findConfig(tenantId, configApiId)
      ?: throw ResourceNotFoundException(
        WorkbenchErrorCode.RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND
      )

  suspend fun templateContext(
    request: WorkItemTemplateContextRequest
  ): WorkItemValueTemplateContext {
    val currentUserApiId =
      repository.resolveUserApiId(request.actorUserId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)
    val currentProjectApiId =
      repository.resolveProjectApiId(request.tenantId, request.projectId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
    return WorkItemValueTemplateContext(
      tenantId = request.tenantId,
      projectId = request.projectId,
      currentUserApiId = currentUserApiId.value,
      currentProjectApiId = currentProjectApiId.value,
      actorUserId = request.actorUserId,
      reporterUserId = request.reporterUserId,
      workItem = request.workItem,
      currentProperties = request.currentProperties,
    )
  }
}

package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.events.WorkItemDomainEvents
import ink.doa.workbench.core.workitem.events.WorkItemMutationEvent
import ink.doa.workbench.core.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.template.WorkItemValueTemplateContext
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class WorkItemMutationSupport(
  private val repository: WorkItemRepository,
  private val configs: IssueTypeConfigRepository,
  private val events: DomainEventPublisher,
) {
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

  fun publishAndEnqueue(
    result: WorkItemMutationResult,
    activityEnqueueSupport: WorkItemActivityEnqueueSupport,
  ) {
    publish(result)
    activityEnqueueSupport.enqueue(result)
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

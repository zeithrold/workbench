package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.events.WorkItemDomainEvents
import ink.doa.workbench.agile.workitem.events.WorkItemMutationEvent
import ink.doa.workbench.agile.workitem.model.IssueTypeConfigDetails
import ink.doa.workbench.agile.workitem.model.WorkItemMutationResult
import ink.doa.workbench.agile.workitem.template.WorkItemValueTemplateContext
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.kernel.port.messaging.DomainEventPublisher
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

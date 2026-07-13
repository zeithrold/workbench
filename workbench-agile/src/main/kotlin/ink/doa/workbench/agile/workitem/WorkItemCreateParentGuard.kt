package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class WorkItemCreateParentGuard(
  private val repository: WorkItemRepository,
  private val subtypeConstraints: IssueSubtypeConstraintRepository,
) {
  suspend fun resolveAndValidate(
    command: CreateWorkItemCommand,
    issueTypeId: UUID,
  ): WorkItemRecord? {
    val parentIssue = resolveParentIssue(command)
    if (parentIssue == null) {
      validateRootSubtypeAllowed(command, issueTypeId)
      return null
    }
    validateChildSubtypeAllowed(command, issueTypeId, parentIssue)
    return parentIssue
  }

  private suspend fun resolveParentIssue(command: CreateWorkItemCommand): WorkItemRecord? =
    command.parentWorkItemApiId?.let {
      repository.findByApiId(command.tenantId, it)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)
    }

  private suspend fun validateRootSubtypeAllowed(
    command: CreateWorkItemCommand,
    issueTypeId: UUID,
  ) {
    if (subtypeConstraints.isChildOnlyType(command.tenantId, command.projectId, issueTypeId)) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_SUBTYPE_PARENT_REQUIRED)
    }
  }

  private suspend fun validateChildSubtypeAllowed(
    command: CreateWorkItemCommand,
    issueTypeId: UUID,
    parentIssue: WorkItemRecord,
  ) {
    if (parentIssue.projectId != command.projectId) {
      throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_SUBTYPE_CROSS_PROJECT_FORBIDDEN)
    }
    subtypeConstraints.findAllowedChildType(
      tenantId = command.tenantId,
      projectId = command.projectId,
      parentIssueTypeId = parentIssue.issueTypeId,
      childIssueTypeId = issueTypeId,
    ) ?: throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_SUBTYPE_NOT_ALLOWED)
  }
}

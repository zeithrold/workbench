package ink.doa.workbench.core.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommand
import ink.doa.workbench.core.workitem.model.TransitionWorkItemCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemSearchPage
import ink.doa.workbench.core.workitem.query.WorkItemQuery
import java.util.UUID
import kotlinx.serialization.json.JsonElement

data class CreateWorkItemPersistenceCommand(
  val command: CreateWorkItemCommand,
  val issueTypeId: UUID,
  val issueTypeConfigId: UUID,
  val initialStatusId: UUID,
  val propertyValues: List<WorkItemPropertyValue>,
  val parentIssueId: UUID? = null,
)

interface WorkItemRepository {
  suspend fun create(command: CreateWorkItemPersistenceCommand): WorkItemMutationResult

  suspend fun findByApiId(tenantId: UUID, apiId: String): WorkItemRecord?

  suspend fun findByApiId(
    tenantId: UUID,
    projectId: UUID,
    apiId: String,
  ): WorkItemRecord?

  suspend fun listByProject(
    tenantId: UUID,
    projectId: UUID,
    limit: Int = 50,
    offset: Long = 0,
  ): List<WorkItemRecord>

  suspend fun listPropertyValues(
    tenantId: UUID,
    issueId: UUID,
  ): Map<String, JsonElement>

  suspend fun update(
    command: UpdateWorkItemCommand,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult

  suspend fun transition(
    command: TransitionWorkItemCommand,
    fromStatusId: UUID,
    toStatusId: UUID,
    transitionId: UUID,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult

  suspend fun softDelete(command: DeleteWorkItemCommand): WorkItemMutationResult

  suspend fun countChildrenNotInStatusGroups(
    tenantId: UUID,
    issueId: UUID,
    terminalGroups: Set<String>,
  ): Long

  suspend fun resolveUserApiId(userId: UUID): PublicId?

  suspend fun resolveProjectApiId(tenantId: UUID, projectId: UUID): PublicId?
}

interface WorkItemQueryRepository {
  suspend fun search(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    page: WorkItemSearchPageRequest = WorkItemSearchPageRequest(),
  ): WorkItemSearchPage
}

data class WorkItemSearchScope(
  val tenantId: UUID,
  val projectId: UUID? = null,
  val includeArchived: Boolean = false,
  val includeDeleted: Boolean = false,
)

data class WorkItemSearchPageRequest(
  val limit: Int = 50,
  val offset: Long = 0,
) {
  init {
    require(limit in 1..200) { "Work item search limit must be between 1 and 200." }
    require(offset >= 0) { "Work item search offset must not be negative." }
  }
}

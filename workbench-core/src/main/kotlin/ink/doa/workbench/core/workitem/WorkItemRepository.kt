package ink.doa.workbench.core.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.pagination.WorkItemSearchCursor
import ink.doa.workbench.core.common.pagination.WorkItemSearchGroupCursor
import ink.doa.workbench.core.sprint.model.SprintCloseDisposition
import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.DeleteWorkItemCommand
import ink.doa.workbench.core.workitem.model.TransitionPersistenceCommand
import ink.doa.workbench.core.workitem.model.UpdateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemPropertyValue
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemSearchGroupsPage
import ink.doa.workbench.core.workitem.model.WorkItemSearchResult
import ink.doa.workbench.core.workitem.query.WorkItemQuery
import ink.doa.workbench.core.workitem.query.WorkItemSearchGroupScope
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

data class ReassignSprintBatchCommand(
  val tenantId: UUID,
  val projectId: UUID,
  val sourceSprintId: UUID,
  val targetSprintId: UUID?,
  val disposition: SprintCloseDisposition,
  val actorUserId: UUID,
  val operationId: String,
  val limit: Int = 100,
)

data class ReassignSprintBatchResult(
  val processedItems: Int,
  val remainingItems: Int,
  val changedItems: List<WorkItemRecord> = emptyList(),
)

@Suppress("TooManyFunctions")
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

  suspend fun countUnfinishedBySprint(tenantId: UUID, projectId: UUID, sprintId: UUID): Long

  suspend fun listPropertyValues(
    tenantId: UUID,
    issueId: UUID,
  ): Map<String, JsonElement>

  suspend fun update(
    command: UpdateWorkItemCommand,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult

  suspend fun transition(
    command: TransitionPersistenceCommand,
    fromStatusId: UUID,
    toStatusId: UUID,
    transitionId: UUID,
    propertyValues: List<WorkItemPropertyValue>,
  ): WorkItemMutationResult

  suspend fun softDelete(command: DeleteWorkItemCommand): WorkItemMutationResult

  suspend fun reassignSprintBatch(command: ReassignSprintBatchCommand): ReassignSprintBatchResult

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
    groupScope: WorkItemSearchGroupScope = WorkItemSearchGroupScope(),
    page: WorkItemSearchPageRequest = WorkItemSearchPageRequest(),
  ): WorkItemSearchResult

  suspend fun searchGroups(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    page: WorkItemSearchGroupsPageRequest = WorkItemSearchGroupsPageRequest(),
  ): WorkItemSearchGroupsPage
}

data class WorkItemSearchScope(
  val tenantId: UUID,
  val projectId: UUID? = null,
  val includeArchived: Boolean = false,
  val includeDeleted: Boolean = false,
)

data class WorkItemSearchPageRequest(
  val limit: Int = 50,
  val cursor: WorkItemSearchCursor? = null,
) {
  init {
    require(limit in 1..200) { "Work item search limit must be between 1 and 200." }
  }
}

data class WorkItemSearchGroupsPageRequest(
  val groupLimit: Int = 20,
  val groupCursor: WorkItemSearchGroupCursor? = null,
) {
  init {
    require(groupLimit in 1..50) { "Work item search group limit must be between 1 and 50." }
  }
}

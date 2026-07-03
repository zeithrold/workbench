package ink.doa.workbench.core.workitem

import ink.doa.workbench.core.workitem.model.CreateWorkItemCommand
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemSearchPage
import ink.doa.workbench.core.workitem.query.WorkItemQuery
import java.util.UUID

interface WorkItemRepository {
  suspend fun create(command: CreateWorkItemCommand): WorkItemRecord

  suspend fun findByApiId(tenantId: UUID, apiId: String): WorkItemRecord?
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

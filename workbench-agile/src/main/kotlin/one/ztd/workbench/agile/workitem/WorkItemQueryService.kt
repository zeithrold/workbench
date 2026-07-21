package one.ztd.workbench.agile.workitem

import java.util.UUID
import one.ztd.workbench.agile.workitem.model.WorkItemSearchGroupsPage
import one.ztd.workbench.agile.workitem.model.WorkItemSearchResult
import one.ztd.workbench.agile.workitem.query.WorkItemQuery
import one.ztd.workbench.agile.workitem.query.WorkItemQueryValidator
import one.ztd.workbench.agile.workitem.query.WorkItemSearchGroupScope
import org.springframework.stereotype.Service

data class WorkItemSearchActor(val userId: UUID, val userApiId: String)

@Service
class WorkItemQueryService(
  private val repository: WorkItemQueryRepository,
  private val capabilities: WorkItemFieldCapabilityService? = null,
) {
  private val validator = WorkItemQueryValidator()

  suspend fun search(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    groupScope: WorkItemSearchGroupScope = WorkItemSearchGroupScope(),
    page: WorkItemSearchPageRequest = WorkItemSearchPageRequest(),
    actor: WorkItemSearchActor? = null,
  ): WorkItemSearchResult {
    validator.validate(query, groupScope)
    val result = repository.search(scope, query, groupScope, page)
    val projectId = scope.projectId ?: return result
    val searchActor = actor ?: return result
    val capabilityService = capabilities ?: return result
    return result.copy(
      hits =
        capabilityService.attach(
          scope.tenantId,
          projectId,
          searchActor.userId,
          searchActor.userApiId,
          result.hits,
        )
    )
  }

  suspend fun searchGroups(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    page: WorkItemSearchGroupsPageRequest = WorkItemSearchGroupsPageRequest(),
  ): WorkItemSearchGroupsPage {
    validator.validate(query)
    requireNotNull(query.group) { "Work item groups query requires query.group." }
    return repository.searchGroups(scope, query, page)
  }
}

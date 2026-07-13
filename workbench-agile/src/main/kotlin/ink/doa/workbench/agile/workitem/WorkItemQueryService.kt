package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.workitem.model.WorkItemSearchGroupsPage
import ink.doa.workbench.agile.workitem.model.WorkItemSearchResult
import ink.doa.workbench.agile.workitem.query.WorkItemQuery
import ink.doa.workbench.agile.workitem.query.WorkItemQueryValidator
import ink.doa.workbench.agile.workitem.query.WorkItemSearchGroupScope
import org.springframework.stereotype.Service

@Service
class WorkItemQueryService(private val repository: WorkItemQueryRepository) {
  private val validator = WorkItemQueryValidator()

  suspend fun search(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    groupScope: WorkItemSearchGroupScope = WorkItemSearchGroupScope(),
    page: WorkItemSearchPageRequest = WorkItemSearchPageRequest(),
  ): WorkItemSearchResult {
    validator.validate(query, groupScope)
    return repository.search(scope, query, groupScope, page)
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

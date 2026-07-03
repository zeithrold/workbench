package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.workitem.WorkItemQueryRepository
import ink.doa.workbench.core.workitem.WorkItemSearchPageRequest
import ink.doa.workbench.core.workitem.WorkItemSearchScope
import ink.doa.workbench.core.workitem.model.WorkItemSearchPage
import ink.doa.workbench.core.workitem.query.WorkItemQuery
import ink.doa.workbench.core.workitem.query.WorkItemQueryValidator
import org.springframework.stereotype.Service

@Service
class WorkItemQueryService(private val repository: WorkItemQueryRepository) {
  private val validator = WorkItemQueryValidator()

  suspend fun search(
    scope: WorkItemSearchScope,
    query: WorkItemQuery,
    page: WorkItemSearchPageRequest = WorkItemSearchPageRequest(),
  ): WorkItemSearchPage {
    validator.validate(query)
    return repository.search(scope, query, page)
  }
}

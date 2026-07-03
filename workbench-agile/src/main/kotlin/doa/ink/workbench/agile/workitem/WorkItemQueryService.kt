package doa.ink.workbench.agile.workitem

import doa.ink.workbench.core.workitem.WorkItemQueryRepository
import doa.ink.workbench.core.workitem.WorkItemSearchPageRequest
import doa.ink.workbench.core.workitem.WorkItemSearchScope
import doa.ink.workbench.core.workitem.model.WorkItemSearchPage
import doa.ink.workbench.core.workitem.query.WorkItemQuery
import doa.ink.workbench.core.workitem.query.WorkItemQueryValidator
import org.springframework.stereotype.Service

@Service
class WorkItemQueryService(
  private val repository: WorkItemQueryRepository,
) {
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

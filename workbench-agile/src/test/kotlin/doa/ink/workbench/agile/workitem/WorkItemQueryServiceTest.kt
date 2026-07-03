package doa.ink.workbench.agile.workitem

import doa.ink.workbench.core.workitem.WorkItemQueryRepository
import doa.ink.workbench.core.workitem.WorkItemSearchPageRequest
import doa.ink.workbench.core.workitem.WorkItemSearchScope
import doa.ink.workbench.core.workitem.model.WorkItemSearchPage
import doa.ink.workbench.core.workitem.model.WorkItemSearchPageInfo
import doa.ink.workbench.core.workitem.model.WorkItemSearchResult
import doa.ink.workbench.core.workitem.query.ConditionNode
import doa.ink.workbench.core.workitem.query.QueryField
import doa.ink.workbench.core.workitem.query.QueryOperator
import doa.ink.workbench.core.workitem.query.QueryValue
import doa.ink.workbench.core.workitem.query.WorkItemQuery
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.serialization.json.JsonPrimitive

class WorkItemQueryServiceTest :
  StringSpec({
    "validates then delegates search to repository" {
      val repository = mockk<WorkItemQueryRepository>()
      val scope = WorkItemSearchScope(tenantId = UUID.randomUUID())
      val page = WorkItemSearchPageRequest(limit = 25)
      val query =
        WorkItemQuery(
          where =
            ConditionNode.Predicate(
              QueryField.System("statusGroup"),
              QueryOperator.EQ,
              QueryValue.Literal(JsonPrimitive("todo")),
            )
        )
      val expected =
        WorkItemSearchPage(
          result = WorkItemSearchResult(hits = emptyList(), total = 0),
          page = WorkItemSearchPageInfo(limit = 25, offset = 0, nextOffset = null),
        )
      coEvery { repository.search(scope, query, page) } returns expected

      val result = WorkItemQueryService(repository).search(scope, query, page)

      result shouldBe expected
      coVerify(exactly = 1) { repository.search(scope, query, page) }
    }

    "rejects invalid queries before repository call" {
      val repository = mockk<WorkItemQueryRepository>()
      val scope = WorkItemSearchScope(tenantId = UUID.randomUUID())
      val query =
        WorkItemQuery(
          where = ConditionNode.Predicate(QueryField.System("unknown"), QueryOperator.EQ, null)
        )

      shouldThrow<RuntimeException> { WorkItemQueryService(repository).search(scope, query) }

      coVerify(exactly = 0) { repository.search(any(), any(), any()) }
    }
  })

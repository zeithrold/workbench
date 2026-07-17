package one.ztd.workbench.agile.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.model.WorkItemSearchResult
import one.ztd.workbench.agile.workitem.query.ConditionNode
import one.ztd.workbench.agile.workitem.query.QueryField
import one.ztd.workbench.agile.workitem.query.QueryOperator
import one.ztd.workbench.agile.workitem.query.QueryValue
import one.ztd.workbench.agile.workitem.query.WorkItemQuery
import one.ztd.workbench.agile.workitem.query.WorkItemSearchGroupScope

class WorkItemQueryServiceTest :
  StringSpec({
    "validates then delegates search to repository" {
      val repository = mockk<WorkItemQueryRepository>()
      val scope = WorkItemSearchScope(tenantId = UUID.randomUUID())
      val page = WorkItemSearchPageRequest(limit = 25)
      val groupScope = WorkItemSearchGroupScope()
      val query =
        WorkItemQuery(
          where =
            ConditionNode.Predicate(
              QueryField.System("statusGroup"),
              QueryOperator.EQ,
              QueryValue.Literal(JsonPrimitive("todo")),
            )
        )
      val expected = WorkItemSearchResult(hits = emptyList(), nextCursor = null)
      coEvery { repository.search(scope, query, groupScope, page) } returns expected

      val result = WorkItemQueryService(repository).search(scope, query, groupScope, page)

      result shouldBe expected
      coVerify(exactly = 1) { repository.search(scope, query, groupScope, page) }
    }

    "rejects invalid queries before repository call" {
      val repository = mockk<WorkItemQueryRepository>()
      val scope = WorkItemSearchScope(tenantId = UUID.randomUUID())
      val query =
        WorkItemQuery(
          where = ConditionNode.Predicate(QueryField.System("unknown"), QueryOperator.EQ, null)
        )

      shouldThrow<RuntimeException> { WorkItemQueryService(repository).search(scope, query) }

      coVerify(exactly = 0) { repository.search(any(), any(), any(), any()) }
    }
  })

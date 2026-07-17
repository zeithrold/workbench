package one.ztd.workbench.agile.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import one.ztd.workbench.agile.testfixtures.AgileWorkItemFixtures
import one.ztd.workbench.agile.workitem.model.WorkItemMutationResult
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException

class WorkItemReadModelServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val record =
      AgileWorkItemFixtures.sampleIssue(
        tenantId,
        projectId,
        AgileWorkItemFixtures.sampleConfig(tenantId),
        UUID.randomUUID(),
      )
    val hit = AgileWorkItemFixtures.searchHit(record)
    val scope = WorkItemSearchScope(tenantId, projectId)

    "detail reads the shared query projection" {
      val queries = mockk<WorkItemQueryRepository>()
      coEvery { queries.findByApiId(scope, record.apiId.value) } returns hit

      WorkItemReadModelService(queries).get(tenantId, projectId, record.apiId.value) shouldBe hit
      coVerify(exactly = 1) { queries.findByApiId(scope, record.apiId.value) }
    }

    "mutation results are reloaded through the shared query projection" {
      val queries = mockk<WorkItemQueryRepository>()
      coEvery { queries.findByApiId(scope, record.apiId.value) } returns hit

      WorkItemReadModelService(queries)
        .afterMutation(WorkItemMutationResult(record, "work_item.updated")) shouldBe hit
      coVerify(exactly = 1) { queries.findByApiId(scope, record.apiId.value) }
    }

    "missing projections use the work item not found error" {
      val queries = mockk<WorkItemQueryRepository>()
      coEvery { queries.findByApiId(scope, "iss_missing") } returns null

      shouldThrow<ResourceNotFoundException> {
        WorkItemReadModelService(queries).get(tenantId, projectId, "iss_missing")
      }
    }
  })

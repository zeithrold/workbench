package ink.doa.workbench.web.api.warning

import ink.doa.workbench.core.common.summary.ProjectSummary
import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.common.warning.InMemoryWorkbenchWarningCollector
import ink.doa.workbench.core.common.warning.WorkbenchWarning
import ink.doa.workbench.core.common.warning.WorkbenchWarningCode
import ink.doa.workbench.core.common.warning.WorkbenchWarningConstants
import ink.doa.workbench.core.common.warning.meta.ProjectDestroyScheduledMeta
import ink.doa.workbench.core.common.warning.meta.TenantDestroyScheduledMeta
import ink.doa.workbench.core.common.warning.meta.WarningTruncatedMeta
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class WorkbenchWarningSupportTest {
  private val support = WorkbenchWarningSupport()
  private val project =
    ProjectSummary(
      id = "prj_01JABCDEFGHJKMNPQRSTVWXYZ0",
      identifier = "WB",
      name = "Workbench",
    )

  @Test
  fun `serializes typed embed meta with kind discriminator`() {
    val json =
      support.toHeaderValue(
        listOf(
          WorkbenchWarning(
            code = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
            message = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED.defaultMessage,
            severity = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED.defaultSeverity,
            meta = ProjectDestroyScheduledMeta(project = project, deleteReason = "cleanup"),
          )
        )
      )

    json shouldContain "\"kind\":\"projectDestroyScheduled\""
    json shouldContain "\"project\":{\"id\":\"prj_01JABCDEFGHJKMNPQRSTVWXYZ0\""
    json shouldContain "\"deleteReason\":\"cleanup\""
  }

  @Test
  fun `prepareForHeader appends truncated marker when item count exceeds max`() {
    val warnings =
      List(WorkbenchWarningConstants.MaxItems + 1) {
        WorkbenchWarning(
          code = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
          message = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED.defaultMessage,
          severity = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED.defaultSeverity,
          meta = ProjectDestroyScheduledMeta(project = project),
        )
      }

    val prepared = support.prepareForHeader(warnings)

    prepared shouldHaveSize WorkbenchWarningConstants.MaxItems + 1
    prepared.last().meta shouldBe WarningTruncatedMeta
  }

  @Test
  fun `header json round trips through envelope schema`() {
    val json =
      support.toHeaderValue(
        listOf(
          WorkbenchWarning(
            code = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
            message = "Project destruction has been scheduled.",
            severity = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED.defaultSeverity,
            meta = ProjectDestroyScheduledMeta(project = project),
          )
        )
      )

    val envelope = jacksonObjectMapper().readValue<WorkbenchWarningEnvelope>(json)
    envelope.version shouldBe 1
    envelope.items.single().meta.kind shouldBe "projectDestroyScheduled"
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  fun `toHeaderValue trims warnings to fit header byte budget`() {
    val longMessage = "x".repeat(500)
    val warnings =
      List(12) {
        WorkbenchWarning(
          code = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
          message = longMessage,
          severity = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED.defaultSeverity,
          meta = ProjectDestroyScheduledMeta(project = project),
        )
      }

    val json = support.toHeaderValue(warnings)

    json.toByteArray(Charsets.UTF_8).size shouldBeLessThanOrEqual
      WorkbenchWarningConstants.MaxHeaderBytes
    json shouldContain "warning.truncated"
  }

  @Test
  fun `toHeaderValue rejects unsupported warning meta types`() {
    shouldThrow<IllegalStateException> {
      support.toHeaderValue(
        listOf(
          WorkbenchWarning(
            code = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
            message = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED.defaultMessage,
            severity = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED.defaultSeverity,
            meta =
              TenantDestroyScheduledMeta(
                tenant =
                  TenantSummary(
                    id = "ten_01JABCDEFGHJKMNPQRSTVWXYZ1",
                    name = "Acme",
                    slug = "acme",
                  )
              ),
          )
        )
      )
    }
  }
}

class WorkbenchWarningFilterTest {
  private val support = WorkbenchWarningSupport()

  @Test
  fun `writes warning header only for successful responses`() {
    val collector = InMemoryWorkbenchWarningCollector()
    collector.warn(
      WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
      ProjectDestroyScheduledMeta(
        project =
          ProjectSummary(
            id = "prj_01JABCDEFGHJKMNPQRSTVWXYZ0",
            identifier = "WB",
            name = "Workbench",
          )
      ),
    )
    val filter = filterFor(collector)
    val request = MockHttpServletRequest()
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, MockFilterChain())

    response.getHeader(WorkbenchWarningConstants.HeaderName) shouldContain
      "project.destroy.scheduled"
    collector.drain() shouldHaveSize 0
  }

  @Test
  fun `does not write warning header for error responses`() {
    val collector = InMemoryWorkbenchWarningCollector()
    collector.warn(
      WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
      ProjectDestroyScheduledMeta(
        project =
          ProjectSummary(
            id = "prj_01JABCDEFGHJKMNPQRSTVWXYZ0",
            identifier = "WB",
            name = "Workbench",
          )
      ),
    )
    val filter = filterFor(collector)
    val request = MockHttpServletRequest()
    val response =
      MockHttpServletResponse().apply {
        status = HttpServletResponse.SC_FORBIDDEN
      }

    filter.doFilter(request, response, MockFilterChain())

    response.getHeader(WorkbenchWarningConstants.HeaderName) shouldBe null
    collector.drain() shouldHaveSize 1
  }

  private fun filterFor(collector: InMemoryWorkbenchWarningCollector): WorkbenchWarningFilter {
    val provider =
      mockk<ObjectProvider<ink.doa.workbench.core.common.warning.WorkbenchWarningCollector>>()
    every { provider.ifAvailable } returns collector
    return WorkbenchWarningFilter(provider, support)
  }
}

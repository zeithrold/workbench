package one.ztd.workbench.web.api.warning

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import one.ztd.workbench.agile.project.ProjectSummary
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.kernel.common.warning.WorkbenchWarning
import one.ztd.workbench.kernel.common.warning.WorkbenchWarningCode
import one.ztd.workbench.kernel.common.warning.meta.ProjectDestroyScheduledMeta
import org.junit.jupiter.api.Test

class RequestScopedWorkbenchWarningCollectorTest {
  private val project =
    ProjectSummary(
      id = PublicId("prj_01JABCDEFGHJKMNPQRSTVWXYZ0"),
      identifier = "WB",
      name = "Workbench",
    )

  @Test
  fun `warn overload builds warning with custom message`() {
    val collector = RequestScopedWorkbenchWarningCollector()
    val meta = ProjectDestroyScheduledMeta(project = project, deleteReason = "cleanup")

    collector.warn(
      WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
      meta,
      message = "custom message",
    )

    val drained = collector.drain()
    drained shouldHaveSize 1
    drained.single().message shouldBe "custom message"
    drained.single().meta shouldBe meta
  }

  @Test
  fun `warn deduplicates identical warnings and drain clears state`() {
    val collector = RequestScopedWorkbenchWarningCollector()
    val warning =
      WorkbenchWarning(
        code = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
        message = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED.defaultMessage,
        severity = WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED.defaultSeverity,
        meta = ProjectDestroyScheduledMeta(project = project),
      )

    collector.warn(warning)
    collector.warn(warning)

    collector.drain() shouldHaveSize 1
    collector.drain().shouldBeEmpty()
  }
}

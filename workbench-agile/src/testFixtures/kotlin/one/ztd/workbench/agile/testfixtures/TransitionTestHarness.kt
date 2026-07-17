package one.ztd.workbench.agile.testfixtures

import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import one.ztd.workbench.agile.workitem.IssueTypeConfigRepository
import one.ztd.workbench.agile.workitem.WorkItemRepository
import one.ztd.workbench.agile.workitem.WorkItemTransitionService
import one.ztd.workbench.agile.workitem.WorkflowConfigurationRepository
import one.ztd.workbench.agile.workitem.template.TransitionFieldsParser
import one.ztd.workbench.kernel.port.messaging.DomainEventPublisher

class TransitionTestHarness(
  val repository: WorkItemRepository = mockk(),
  val configs: IssueTypeConfigRepository = mockk(),
  val workflows: WorkflowConfigurationRepository = mockk(),
  val events: DomainEventPublisher = mockk(relaxed = true),
  val clock: Clock = Clock.fixed(Instant.parse("2026-07-04T10:15:30Z"), ZoneOffset.UTC),
  val transitionFieldsParser: TransitionFieldsParser = TransitionFieldsParser(),
) {
  val service: WorkItemTransitionService =
    AgileServiceFactory.workItemTransitionService(
      repository = repository,
      configs = configs,
      workflows = workflows,
      events = events,
      clock = clock,
      transitionFieldsParser = transitionFieldsParser,
    )
}

package ink.doa.workbench.agile.testfixtures

import ink.doa.workbench.agile.workitem.WorkItemTransitionService
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.WorkflowConfigurationRepository
import ink.doa.workbench.core.workitem.template.TransitionFieldsParser
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

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

package ink.doa.workbench.application.project

import ink.doa.workbench.kernel.common.warning.WorkbenchWarningCollector
import ink.doa.workbench.kernel.port.messaging.DomainEventPublisher
import java.time.Clock
import org.springframework.stereotype.Component

@Component
class ProjectManagementInfrastructure(
  val domainEventPublisher: DomainEventPublisher,
  val warningCollector: WorkbenchWarningCollector,
  val clock: Clock,
)

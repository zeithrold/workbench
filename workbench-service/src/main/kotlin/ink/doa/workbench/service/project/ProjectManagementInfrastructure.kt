package ink.doa.workbench.service.project

import ink.doa.workbench.core.common.warning.WorkbenchWarningCollector
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import java.time.Clock
import org.springframework.stereotype.Component

@Component
class ProjectManagementInfrastructure(
  val domainEventPublisher: DomainEventPublisher,
  val warningCollector: WorkbenchWarningCollector,
  val clock: Clock,
)

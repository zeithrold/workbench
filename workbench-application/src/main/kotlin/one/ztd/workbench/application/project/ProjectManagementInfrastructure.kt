package one.ztd.workbench.application.project

import java.time.Clock
import one.ztd.workbench.kernel.common.warning.WorkbenchWarningCollector
import one.ztd.workbench.kernel.port.messaging.DomainEventPublisher
import org.springframework.stereotype.Component

@Component
class ProjectManagementInfrastructure(
  val domainEventPublisher: DomainEventPublisher,
  val warningCollector: WorkbenchWarningCollector,
  val clock: Clock,
)

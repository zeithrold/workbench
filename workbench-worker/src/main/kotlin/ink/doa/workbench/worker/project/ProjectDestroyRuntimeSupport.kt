package ink.doa.workbench.worker.project

import ink.doa.workbench.agile.project.ProjectDestructionService
import ink.doa.workbench.core.port.locking.DistributedLockService
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import java.time.Clock
import org.springframework.stereotype.Component

@Component
class ProjectDestroyRuntimeSupport(
  val projectDestructionService: ProjectDestructionService,
  val domainEventPublisher: DomainEventPublisher,
  val distributedLockService: DistributedLockService,
  val clock: Clock,
)

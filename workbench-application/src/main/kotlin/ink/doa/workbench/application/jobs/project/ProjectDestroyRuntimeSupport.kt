package ink.doa.workbench.application.jobs.project

import ink.doa.workbench.agile.project.ProjectDestructionService
import ink.doa.workbench.kernel.port.locking.DistributedLockService
import ink.doa.workbench.kernel.port.messaging.DomainEventPublisher
import java.time.Clock
import org.springframework.stereotype.Component

@ink.doa.workbench.application.jobs.JobsEnabled
@Component
class ProjectDestroyRuntimeSupport(
  val projectDestructionService: ProjectDestructionService,
  val domainEventPublisher: DomainEventPublisher,
  val distributedLockService: DistributedLockService,
  val clock: Clock,
)

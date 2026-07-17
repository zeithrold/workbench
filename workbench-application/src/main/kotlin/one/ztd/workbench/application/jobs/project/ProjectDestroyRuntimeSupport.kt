package one.ztd.workbench.application.jobs.project

import java.time.Clock
import one.ztd.workbench.agile.project.ProjectDestructionService
import one.ztd.workbench.kernel.port.locking.DistributedLockService
import one.ztd.workbench.kernel.port.messaging.DomainEventPublisher
import org.springframework.stereotype.Component

@one.ztd.workbench.application.jobs.JobsEnabled
@Component
class ProjectDestroyRuntimeSupport(
  val projectDestructionService: ProjectDestructionService,
  val domainEventPublisher: DomainEventPublisher,
  val distributedLockService: DistributedLockService,
  val clock: Clock,
)

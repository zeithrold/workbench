package ink.doa.workbench.core.workitem.activity

import ink.doa.workbench.core.common.ids.PublicId
import java.util.UUID

data class PendingWorkItemActivity(
  val id: UUID,
  val apiId: PublicId,
  val command: CreateWorkItemActivityCommand<*>,
)

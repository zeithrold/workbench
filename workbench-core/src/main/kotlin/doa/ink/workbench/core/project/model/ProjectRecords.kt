package doa.ink.workbench.core.project.model

import doa.ink.workbench.core.common.ids.PublicId
import java.util.UUID

data class ProjectRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val identifier: String,
  val name: String,
  val description: String?,
)

data class CreateProjectCommand(
  val tenantId: UUID,
  val identifier: String,
  val name: String,
  val description: String?,
)

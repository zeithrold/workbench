package one.ztd.workbench.agile.project.model

import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.kernel.common.ids.PublicId

data class ProjectRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val identifier: String,
  val name: String,
  val description: String?,
  val status: ProjectStatus = ProjectStatus.ACTIVE,
  val nonMemberVisibility: NonMemberVisibility = NonMemberVisibility.INVISIBLE,
  val nonMemberJoinPolicy: NonMemberJoinPolicy = NonMemberJoinPolicy.ADMIN_ONLY,
  val leadUserId: UUID? = null,
  val createdBy: UUID? = null,
  val archivedAt: OffsetDateTime? = null,
  val archivedBy: UUID? = null,
  val deletedAt: OffsetDateTime? = null,
)

data class CreateProjectCommand(
  val tenantId: UUID,
  val identifier: String,
  val name: String,
  val description: String?,
  val createdBy: UUID,
  val leadUserId: UUID,
)

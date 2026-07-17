package one.ztd.workbench.agile.workitem.view

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import one.ztd.workbench.kernel.common.ids.PublicId

data class WorkItemViewRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val projectId: UUID?,
  val ownerId: UUID,
  val ownerApiId: PublicId,
  val name: String,
  val description: String?,
  val visibility: WorkItemViewVisibility,
  val queryAst: JsonElement,
  val displayFields: JsonElement,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
)

data class CreateWorkItemViewCommand(
  val tenantId: UUID,
  val projectId: UUID?,
  val ownerId: UUID,
  val name: String,
  val description: String?,
  val visibility: WorkItemViewVisibility,
  val queryAst: JsonElement,
  val displayFields: JsonElement,
)

data class UpdateWorkItemViewCommand(
  val tenantId: UUID,
  val viewApiId: String,
  val projectId: UUID?,
  val actorUserId: UUID,
  val name: String? = null,
  val description: String? = null,
  val visibility: WorkItemViewVisibility? = null,
  val queryAst: JsonElement? = null,
  val displayFields: JsonElement? = null,
)

data class DeleteWorkItemViewCommand(
  val tenantId: UUID,
  val viewApiId: String,
  val projectId: UUID?,
  val actorUserId: UUID,
)

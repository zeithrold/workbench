package ink.doa.workbench.agile.workitem.activity

import ink.doa.workbench.kernel.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID

data class CreateWorkItemActivityCommand<T : Any>(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemId: UUID,
  val actorUserId: UUID?,
  val spec: WorkItemActivitySpec<T>,
  val payload: T,
  val occurredAt: OffsetDateTime,
  val summary: String? = null,
  val sourceType: WorkItemActivitySourceType = WorkItemActivitySourceType.USER,
  val sourceId: String? = null,
  val correlationId: String? = null,
  val requestId: String? = null,
)

data class WorkItemActivityRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val projectId: UUID,
  val workItemId: UUID,
  val workItemApiId: PublicId,
  val actorUserId: UUID?,
  val actorApiId: PublicId?,
  val actorDisplayName: String?,
  val activityType: WorkItemActivityType,
  val occurredAt: OffsetDateTime,
  val summary: String?,
  val payload: WorkItemActivityPayload,
  val sourceType: WorkItemActivitySourceType,
  val createdAt: OffsetDateTime,
)

package ink.doa.workbench.core.workitem.stream

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.activity.WorkItemActivityPayload
import java.time.OffsetDateTime
import java.util.UUID

data class AppendWorkItemEventCommand<T : Any>(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemId: UUID,
  val actorUserId: UUID?,
  val spec: WorkItemEventSpec<T>,
  val payload: T,
  val occurredAt: OffsetDateTime,
  val summary: String? = null,
  val sourceType: WorkItemEventSourceType = WorkItemEventSourceType.USER,
  val sourceId: String? = null,
  val correlationId: String? = null,
  val requestId: String? = null,
)

data class WorkItemEventRecord(
  val id: UUID,
  val apiId: PublicId,
  val tenantId: UUID,
  val projectId: UUID,
  val workItemId: UUID,
  val workItemApiId: PublicId,
  val sequence: Long,
  val eventType: WorkItemEventType,
  val actorUserId: UUID?,
  val actorApiId: PublicId?,
  val actorDisplayName: String?,
  val occurredAt: OffsetDateTime,
  val summary: String?,
  val payload: WorkItemActivityPayload,
  val sourceType: WorkItemEventSourceType,
  val createdAt: OffsetDateTime,
)

package ink.doa.workbench.web.messaging

import ink.doa.workbench.application.messaging.OutboxDeliveryAdminApplicationService
import ink.doa.workbench.application.messaging.OutboxDeliveryQuery
import ink.doa.workbench.application.messaging.OutboxDeliveryRecord
import ink.doa.workbench.web.api.Audit
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.InstanceScoped
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.context.InstanceRequestContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/outbox/deliveries")
@Tag(name = "Outbox Administration", description = "Instance-scoped domain outbox operations.")
@SessionSecured
@StandardErrorResponses
class OutboxDeliveryAdminController(private val service: OutboxDeliveryAdminApplicationService) {
  @GetMapping
  @Authenticated
  @InstanceScoped
  @Authorize(action = "outbox.read", resource = "outbox")
  @Operation(summary = "List outbox deliveries")
  @Suppress("RedundantSuspendModifier")
  suspend fun list(
    @ModelAttribute query: OutboxDeliveryQuery,
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext,
  ): List<OutboxDeliveryResponse> = service.list(query).map(OutboxDeliveryResponse::from)

  @PostMapping("/{outboxId}/consumers/{consumerName}/replay")
  @Authenticated
  @InstanceScoped
  @Authorize(action = "outbox.manage", resource = "outbox")
  @Audit("outbox.delivery.replay")
  @Operation(summary = "Replay a dead-letter outbox delivery")
  @Suppress("RedundantSuspendModifier")
  suspend fun replay(
    @PathVariable outboxId: UUID,
    @PathVariable consumerName: String,
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext,
  ): OutboxDeliveryResponse = OutboxDeliveryResponse.from(service.replay(outboxId, consumerName))
}

data class OutboxDeliveryResponse(
  val outboxId: UUID,
  val consumerName: String,
  val partitionKey: String,
  val status: String,
  val attempts: Int,
  val nextAttemptAt: OffsetDateTime,
  val lockedUntil: OffsetDateTime?,
  val lastError: String?,
  val completedAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
) {
  companion object {
    fun from(record: OutboxDeliveryRecord) =
      OutboxDeliveryResponse(
        record.outboxId,
        record.consumerName,
        record.partitionKey,
        record.status,
        record.attempts,
        record.nextAttemptAt,
        record.lockedUntil,
        record.lastError,
        record.completedAt,
        record.createdAt,
        record.updatedAt,
      )
  }
}

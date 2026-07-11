package ink.doa.workbench.web.messaging

import ink.doa.workbench.core.common.context.InstanceRequestContext
import ink.doa.workbench.core.messaging.OutboxMessageQuery
import ink.doa.workbench.core.messaging.OutboxMessageRecord
import ink.doa.workbench.service.messaging.OutboxAdminApplicationService
import ink.doa.workbench.web.api.Audit
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.InstanceScoped
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
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
@RequestMapping("/api/admin/outbox/messages")
@Tag(name = "Outbox Administration", description = "Instance-scoped domain outbox operations.")
@SessionSecured
@StandardErrorResponses
class OutboxAdminController(private val service: OutboxAdminApplicationService) {
  @GetMapping
  @Authenticated
  @InstanceScoped
  @Authorize(action = "outbox.read", resource = "outbox")
  @Operation(summary = "List outbox messages")
  @Suppress("RedundantSuspendModifier") // Security aspects require a suspend endpoint signature.
  suspend fun list(
    @ModelAttribute query: OutboxMessageQuery,
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext,
  ): List<OutboxMessageResponse> = service.list(query).map(OutboxMessageResponse::from)

  @GetMapping("/{id}")
  @Authenticated
  @InstanceScoped
  @Authorize(action = "outbox.read", resource = "outbox")
  @Operation(summary = "Get an outbox message")
  @Suppress("RedundantSuspendModifier") // Security aspects require a suspend endpoint signature.
  suspend fun get(
    @PathVariable id: UUID,
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext,
  ): OutboxMessageResponse = OutboxMessageResponse.from(service.get(id))

  @PostMapping("/{id}/replay")
  @Authenticated
  @InstanceScoped
  @Authorize(action = "outbox.manage", resource = "outbox")
  @Audit("outbox.replay")
  @Operation(summary = "Replay a dead-letter outbox message")
  @Suppress("RedundantSuspendModifier") // Security aspects require a suspend endpoint signature.
  suspend fun replay(
    @PathVariable id: UUID,
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext,
  ): OutboxMessageResponse = OutboxMessageResponse.from(service.replay(id))
}

data class OutboxMessageResponse(
  val id: UUID,
  val eventId: String,
  val eventType: String,
  val topic: String,
  val partitionKey: String,
  val tenantId: String?,
  val status: String,
  val attempts: Int,
  val lastError: String?,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
  val nextAttemptAt: OffsetDateTime,
  val publishedAt: OffsetDateTime?,
) {
  companion object {
    fun from(record: OutboxMessageRecord) =
      OutboxMessageResponse(
        id = record.id,
        eventId = record.eventId,
        eventType = record.eventType,
        topic = record.topic,
        partitionKey = record.partitionKey,
        tenantId = record.tenantId,
        status = record.status,
        attempts = record.attempts,
        lastError = record.lastError,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
        nextAttemptAt = record.nextAttemptAt,
        publishedAt = record.publishedAt,
      )
  }
}

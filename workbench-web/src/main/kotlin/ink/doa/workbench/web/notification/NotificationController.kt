package ink.doa.workbench.web.notification

import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.service.notification.NotificationApplicationService
import ink.doa.workbench.service.notification.NotificationView
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "In-app notifications and notification preferences.")
@SessionSecured
@StandardErrorResponses
class NotificationController(private val service: NotificationApplicationService) {
  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "notification.read", resource = "notification")
  @Operation(summary = "List notifications")
  suspend fun list(
    @RequestParam(defaultValue = "50") limit: Int,
    @RequestParam(defaultValue = "0") offset: Long,
    tenantContext: TenantRequestContext,
  ): List<NotificationResponse> =
    service
      .list(actorId(tenantContext), tenantContext.tenant.id, limit, offset)
      .map(NotificationResponse::from)

  @GetMapping("/unread-count")
  @Authenticated
  @TenantScoped
  @Authorize(action = "notification.read", resource = "notification")
  @Operation(summary = "Get unread notification count")
  suspend fun unreadCount(tenantContext: TenantRequestContext): UnreadCountResponse =
    UnreadCountResponse(service.unreadCount(actorId(tenantContext), tenantContext.tenant.id))

  @PostMapping("/{id}/read")
  @Authenticated
  @TenantScoped
  @Authorize(action = "notification.read", resource = "notification")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Mark a notification as read")
  suspend fun markRead(@PathVariable id: String, tenantContext: TenantRequestContext) {
    service.markRead(actorId(tenantContext), tenantContext.tenant.id, id)
  }

  @PostMapping("/read-all")
  @Authenticated
  @TenantScoped
  @Authorize(action = "notification.read", resource = "notification")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Mark all notifications as read")
  suspend fun markAllRead(tenantContext: TenantRequestContext) {
    service.markAllRead(actorId(tenantContext), tenantContext.tenant.id)
  }

  @GetMapping("/preferences")
  @Authenticated
  @TenantScoped
  @Authorize(action = "notification.read", resource = "notification")
  @Operation(summary = "List notification preferences")
  suspend fun preferences(
    tenantContext: TenantRequestContext
  ): List<NotificationPreferenceResponse> =
    service.listPreferences(actorId(tenantContext)).map(NotificationPreferenceResponse::from)

  @PutMapping("/preferences")
  @Authenticated
  @TenantScoped
  @Authorize(action = "notification.manage", resource = "notification")
  @Operation(summary = "Update a notification preference")
  suspend fun updatePreference(
    @Valid @RequestBody request: UpdateNotificationPreferenceRequest,
    tenantContext: TenantRequestContext,
  ): NotificationPreferenceResponse =
    NotificationPreferenceResponse.from(
      service.updatePreference(
        userId = actorId(tenantContext),
        notificationType = request.notificationType,
        inAppEnabled = request.inAppEnabled,
        emailEnabled = request.emailEnabled,
      )
    )

  private fun actorId(context: TenantRequestContext) =
    context.actor?.id
      ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
}

data class NotificationResponse(
  val id: String,
  val projectId: String?,
  val workItemId: String?,
  val notificationType: String,
  val title: String,
  val body: String,
  val payload: kotlinx.serialization.json.JsonElement,
  val readAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
) {
  companion object {
    fun from(view: NotificationView) =
      NotificationResponse(
        id = view.id,
        projectId = view.projectId?.toString(),
        workItemId = view.workItemId?.toString(),
        notificationType = view.notificationType,
        title = view.title,
        body = view.body,
        payload = view.payload,
        readAt = view.readAt,
        createdAt = view.createdAt,
      )
  }
}

data class UnreadCountResponse(val count: Long)

data class NotificationPreferenceResponse(
  val notificationType: String,
  val inAppEnabled: Boolean,
  val emailEnabled: Boolean,
) {
  companion object {
    fun from(record: ink.doa.workbench.core.notification.NotificationPreferenceRecord) =
      NotificationPreferenceResponse(
        notificationType = record.notificationType,
        inAppEnabled = record.inAppEnabled,
        emailEnabled = record.emailEnabled,
      )
  }
}

data class UpdateNotificationPreferenceRequest(
  @field:NotBlank val notificationType: String,
  val inAppEnabled: Boolean = true,
  val emailEnabled: Boolean = true,
)

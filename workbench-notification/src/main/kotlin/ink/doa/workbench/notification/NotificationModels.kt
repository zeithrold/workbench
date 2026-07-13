package ink.doa.workbench.notification

import ink.doa.workbench.kernel.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonElement

enum class NotificationChannel {
  IN_APP,
  EMAIL,
}

enum class NotificationDeliveryStatus {
  PENDING,
  SENT,
  RETRY,
  DEAD,
}

data class NotificationRecord(
  val id: UUID,
  val apiId: PublicId,
  val recipientUserId: UUID,
  val tenantId: UUID,
  val projectId: UUID?,
  val workItemId: UUID?,
  val sourceEventId: String,
  val notificationType: String,
  val title: String,
  val body: String,
  val payload: JsonElement,
  val readAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
)

data class NotificationDeliveryRecord(
  val notificationId: UUID,
  val channel: NotificationChannel,
  val status: NotificationDeliveryStatus,
  val attempts: Int,
  val nextAttemptAt: OffsetDateTime,
  val sentAt: OffsetDateTime?,
  val lastError: String?,
)

data class NotificationPreferenceRecord(
  val userId: UUID,
  val notificationType: String,
  val inAppEnabled: Boolean,
  val emailEnabled: Boolean,
)

data class CreateNotificationCommand(
  val recipientUserId: UUID,
  val tenantId: UUID,
  val projectId: UUID?,
  val workItemId: UUID?,
  val sourceEventId: String,
  val notificationType: String,
  val title: String,
  val body: String,
  val payload: JsonElement,
  val channels: Set<NotificationChannel> =
    setOf(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
)

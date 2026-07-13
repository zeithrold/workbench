package ink.doa.workbench.notification

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import org.springframework.stereotype.Service

@Service
class NotificationApplicationService(
  private val repository: NotificationStore,
  private val workItemEvents: WorkItemNotificationEventStore,
  private val clock: Clock,
) {
  suspend fun list(userId: UUID, tenantId: UUID, limit: Int, offset: Long): List<NotificationView> =
    repository.list(userId, tenantId, limit, offset).map(NotificationView::from)

  suspend fun unreadCount(userId: UUID, tenantId: UUID): Long =
    repository.unreadCount(userId, tenantId)

  suspend fun markRead(userId: UUID, tenantId: UUID, notificationApiId: String): Boolean =
    repository.markRead(userId, tenantId, notificationApiId, now())

  suspend fun markAllRead(userId: UUID, tenantId: UUID): Int =
    repository.markAllRead(userId, tenantId, now())

  suspend fun create(command: CreateNotificationCommand): NotificationRecord {
    return repository.create(command.copy(channels = enabledChannels(command)))
  }

  suspend fun processWorkItemEvent(
    consumerName: String,
    eventId: String,
    command: CreateNotificationCommand?,
  ): NotificationRecord? {
    val effectiveCommand = command?.copy(channels = enabledChannels(command))
    return workItemEvents.processIfUnprocessed(consumerName, eventId, effectiveCommand)
  }

  suspend fun listPreferences(userId: UUID): List<NotificationPreferenceRecord> =
    repository.listPreferences(userId)

  suspend fun updatePreference(
    userId: UUID,
    notificationType: String,
    inAppEnabled: Boolean,
    emailEnabled: Boolean,
  ): NotificationPreferenceRecord =
    repository.upsertPreference(
      NotificationPreferenceRecord(
        userId = userId,
        notificationType = notificationType,
        inAppEnabled = inAppEnabled,
        emailEnabled = emailEnabled,
      )
    )

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC))

  private suspend fun enabledChannels(
    command: CreateNotificationCommand
  ): Set<NotificationChannel> {
    val preference = repository.getPreference(command.recipientUserId, command.notificationType)
    return buildSet {
      if (preference?.inAppEnabled != false && NotificationChannel.IN_APP in command.channels) {
        add(NotificationChannel.IN_APP)
      }
      if (preference?.emailEnabled != false && NotificationChannel.EMAIL in command.channels) {
        add(NotificationChannel.EMAIL)
      }
    }
  }
}

data class NotificationView(
  val id: String,
  val recipientUserId: UUID,
  val tenantId: UUID,
  val projectId: UUID?,
  val workItemId: UUID?,
  val notificationType: String,
  val title: String,
  val body: String,
  val payload: JsonElement,
  val readAt: OffsetDateTime?,
  val createdAt: OffsetDateTime,
) {
  companion object {
    fun from(record: NotificationRecord) =
      NotificationView(
        id = record.apiId.value,
        recipientUserId = record.recipientUserId,
        tenantId = record.tenantId,
        projectId = record.projectId,
        workItemId = record.workItemId,
        notificationType = record.notificationType,
        title = record.title,
        body = record.body,
        payload = record.payload,
        readAt = record.readAt,
        createdAt = record.createdAt,
      )
  }
}

package ink.doa.workbench.notification

import java.time.OffsetDateTime
import java.util.UUID

interface NotificationStore {
  suspend fun list(userId: UUID, tenantId: UUID, limit: Int, offset: Long): List<NotificationRecord>

  suspend fun unreadCount(userId: UUID, tenantId: UUID): Long

  suspend fun markRead(
    userId: UUID,
    tenantId: UUID,
    notificationApiId: String,
    readAt: OffsetDateTime,
  ): Boolean

  suspend fun markAllRead(userId: UUID, tenantId: UUID, readAt: OffsetDateTime): Int

  suspend fun create(command: CreateNotificationCommand): NotificationRecord

  suspend fun getPreference(userId: UUID, notificationType: String): NotificationPreferenceRecord?

  suspend fun listPreferences(userId: UUID): List<NotificationPreferenceRecord>

  suspend fun upsertPreference(
    preference: NotificationPreferenceRecord
  ): NotificationPreferenceRecord
}

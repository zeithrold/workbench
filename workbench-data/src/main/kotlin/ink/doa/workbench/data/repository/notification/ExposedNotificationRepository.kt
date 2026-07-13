package ink.doa.workbench.data.repository.notification

import ink.doa.workbench.data.persistence.postgres.notification.NotificationDeliveriesTable
import ink.doa.workbench.data.persistence.postgres.notification.NotificationPreferencesTable
import ink.doa.workbench.data.persistence.postgres.notification.NotificationsTable
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.notification.CreateNotificationCommand
import ink.doa.workbench.notification.NotificationDeliveryStatus
import ink.doa.workbench.notification.NotificationPreferenceRecord
import ink.doa.workbench.notification.NotificationRecord
import ink.doa.workbench.notification.NotificationStore
import ink.doa.workbench.notification.WorkItemNotificationEventStore
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedNotificationRepository(private val database: Database) :
  NotificationStore, WorkItemNotificationEventStore {
  override suspend fun processIfUnprocessed(
    consumerName: String,
    eventId: String,
    command: CreateNotificationCommand?,
  ): NotificationRecord? =
    suspendTransaction(db = database) {
      val claimed =
        exec(
          """
          WITH inserted AS (
            INSERT INTO processed_domain_events (consumer_name, event_id)
            VALUES (?, ?)
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            RETURNING event_id
          )
          SELECT EXISTS (SELECT 1 FROM inserted)
          """
            .trimIndent(),
          listOf(TextColumnType() to consumerName, TextColumnType() to eventId),
          StatementType.SELECT,
        ) { resultSet ->
          resultSet.next() && resultSet.getBoolean(1)
        } ?: false
      if (!claimed || command == null) return@suspendTransaction null
      createInCurrentTransaction(command)
    }

  override suspend fun list(
    userId: UUID,
    tenantId: UUID,
    limit: Int,
    offset: Long,
  ): List<NotificationRecord> =
    suspendTransaction(db = database) {
      NotificationsTable.selectAll()
        .where {
          (NotificationsTable.recipientUserId eq userId.toKotlinUuid()) and
            (NotificationsTable.tenantId eq tenantId.toKotlinUuid())
        }
        .orderBy(NotificationsTable.createdAt to SortOrder.DESC)
        .limit(limit.coerceIn(1, 100))
        .offset(offset.coerceAtLeast(0))
        .map { it.toRecord() }
    }

  override suspend fun unreadCount(userId: UUID, tenantId: UUID): Long =
    suspendTransaction(db = database) {
      NotificationsTable.selectAll()
        .where {
          (NotificationsTable.recipientUserId eq userId.toKotlinUuid()) and
            (NotificationsTable.tenantId eq tenantId.toKotlinUuid()) and
            NotificationsTable.readAt.isNull()
        }
        .count()
    }

  override suspend fun markRead(
    userId: UUID,
    tenantId: UUID,
    notificationApiId: String,
    readAt: OffsetDateTime,
  ): Boolean =
    suspendTransaction(db = database) {
      NotificationsTable.update({
        (NotificationsTable.recipientUserId eq userId.toKotlinUuid()) and
          (NotificationsTable.tenantId eq tenantId.toKotlinUuid()) and
          (NotificationsTable.apiId eq notificationApiId) and
          NotificationsTable.readAt.isNull()
      }) {
        it[NotificationsTable.readAt] = readAt
      } > 0
    }

  override suspend fun markAllRead(userId: UUID, tenantId: UUID, readAt: OffsetDateTime): Int =
    suspendTransaction(db = database) {
      NotificationsTable.update({
        (NotificationsTable.recipientUserId eq userId.toKotlinUuid()) and
          (NotificationsTable.tenantId eq tenantId.toKotlinUuid()) and
          NotificationsTable.readAt.isNull()
      }) {
        it[NotificationsTable.readAt] = readAt
      }
    }

  override suspend fun create(command: CreateNotificationCommand): NotificationRecord =
    suspendTransaction(db = database) {
      createInCurrentTransaction(command)
    }

  private fun createInCurrentTransaction(command: CreateNotificationCommand): NotificationRecord {
    val existing =
      NotificationsTable.selectAll()
        .where {
          (NotificationsTable.sourceEventId eq command.sourceEventId) and
            (NotificationsTable.recipientUserId eq command.recipientUserId.toKotlinUuid()) and
            (NotificationsTable.notificationType eq command.notificationType)
        }
        .singleOrNull()
    if (existing != null) return existing.toRecord()

    val id = UUID.randomUUID()
    val apiId = PublicId.new("ntf")
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    NotificationsTable.insert {
      it[NotificationsTable.id] = id.toKotlinUuid()
      it[NotificationsTable.apiId] = apiId.value
      it[NotificationsTable.recipientUserId] = command.recipientUserId.toKotlinUuid()
      it[NotificationsTable.tenantId] = command.tenantId.toKotlinUuid()
      it[NotificationsTable.projectId] = command.projectId?.toKotlinUuid()
      it[NotificationsTable.workItemId] = command.workItemId?.toKotlinUuid()
      it[NotificationsTable.sourceEventId] = command.sourceEventId
      it[NotificationsTable.notificationType] = command.notificationType
      it[NotificationsTable.title] = command.title
      it[NotificationsTable.body] = command.body
      it[NotificationsTable.payload] = command.payload
      it[NotificationsTable.createdAt] = now
    }
    command.channels.forEach { channel ->
      NotificationDeliveriesTable.insert {
        it[NotificationDeliveriesTable.id] = UUID.randomUUID().toKotlinUuid()
        it[NotificationDeliveriesTable.notificationId] = id.toKotlinUuid()
        it[NotificationDeliveriesTable.channel] = channel.name
        it[NotificationDeliveriesTable.status] = NotificationDeliveryStatus.PENDING.name
        it[NotificationDeliveriesTable.attempts] = 0
        it[NotificationDeliveriesTable.nextAttemptAt] = now
      }
    }
    return NotificationRecord(
      id = id,
      apiId = apiId,
      recipientUserId = command.recipientUserId,
      tenantId = command.tenantId,
      projectId = command.projectId,
      workItemId = command.workItemId,
      sourceEventId = command.sourceEventId,
      notificationType = command.notificationType,
      title = command.title,
      body = command.body,
      payload = command.payload,
      readAt = null,
      createdAt = now,
    )
  }

  override suspend fun getPreference(
    userId: UUID,
    notificationType: String,
  ): NotificationPreferenceRecord? =
    suspendTransaction(db = database) {
      NotificationPreferencesTable.selectAll()
        .where {
          (NotificationPreferencesTable.userId eq userId.toKotlinUuid()) and
            (NotificationPreferencesTable.notificationType eq notificationType)
        }
        .singleOrNull()
        ?.toPreference()
    }

  override suspend fun listPreferences(userId: UUID): List<NotificationPreferenceRecord> =
    suspendTransaction(db = database) {
      NotificationPreferencesTable.selectAll()
        .where { NotificationPreferencesTable.userId eq userId.toKotlinUuid() }
        .orderBy(NotificationPreferencesTable.notificationType to SortOrder.ASC)
        .map { it.toPreference() }
    }

  override suspend fun upsertPreference(
    preference: NotificationPreferenceRecord
  ): NotificationPreferenceRecord =
    suspendTransaction(db = database) {
      val userId = preference.userId.toKotlinUuid()
      val existing =
        NotificationPreferencesTable.selectAll()
          .where {
            (NotificationPreferencesTable.userId eq userId) and
              (NotificationPreferencesTable.notificationType eq preference.notificationType)
          }
          .singleOrNull()
      if (existing == null) {
        NotificationPreferencesTable.insert {
          it[NotificationPreferencesTable.userId] = userId
          it[NotificationPreferencesTable.notificationType] = preference.notificationType
          it[NotificationPreferencesTable.inAppEnabled] = preference.inAppEnabled
          it[NotificationPreferencesTable.emailEnabled] = preference.emailEnabled
        }
      } else {
        NotificationPreferencesTable.update({
          (NotificationPreferencesTable.userId eq userId) and
            (NotificationPreferencesTable.notificationType eq preference.notificationType)
        }) {
          it[NotificationPreferencesTable.inAppEnabled] = preference.inAppEnabled
          it[NotificationPreferencesTable.emailEnabled] = preference.emailEnabled
        }
      }
      preference
    }

  private fun ResultRow.toRecord(): NotificationRecord =
    NotificationRecord(
      id = this[NotificationsTable.id].toJavaUuid(),
      apiId = PublicId(this[NotificationsTable.apiId]),
      recipientUserId = this[NotificationsTable.recipientUserId].toJavaUuid(),
      tenantId = this[NotificationsTable.tenantId].toJavaUuid(),
      projectId = this[NotificationsTable.projectId]?.toJavaUuid(),
      workItemId = this[NotificationsTable.workItemId]?.toJavaUuid(),
      sourceEventId = this[NotificationsTable.sourceEventId],
      notificationType = this[NotificationsTable.notificationType],
      title = this[NotificationsTable.title],
      body = this[NotificationsTable.body],
      payload = this[NotificationsTable.payload],
      readAt = this[NotificationsTable.readAt],
      createdAt = this[NotificationsTable.createdAt],
    )

  private fun ResultRow.toPreference(): NotificationPreferenceRecord =
    NotificationPreferenceRecord(
      userId = this[NotificationPreferencesTable.userId].toJavaUuid(),
      notificationType = this[NotificationPreferencesTable.notificationType],
      inAppEnabled = this[NotificationPreferencesTable.inAppEnabled],
      emailEnabled = this[NotificationPreferencesTable.emailEnabled],
    )
}

package one.ztd.workbench.data.persistence.postgres.notification

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import one.ztd.workbench.data.persistence.postgres.identity.TenantsTable
import one.ztd.workbench.data.persistence.postgres.identity.UsersTable
import one.ztd.workbench.data.persistence.postgres.project.ProjectsTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssuesTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object NotificationsTable : Table("notifications") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val recipientUserId = uuid("recipient_user_id").references(UsersTable.id)
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id).nullable()
  val workItemId = uuid("work_item_id").references(IssuesTable.id).nullable()
  val sourceEventId = text("source_event_id")
  val notificationType = text("notification_type")
  val title = text("title")
  val body = text("body")
  val payload = jsonb("payload", Json.Default, JsonElement.serializer())
  val readAt = timestampWithTimeZone("read_at").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

object NotificationDeliveriesTable : Table("notification_deliveries") {
  val id = uuid("id")
  val notificationId = uuid("notification_id").references(NotificationsTable.id)
  val channel = text("channel")
  val status = text("status")
  val attempts = integer("attempts")
  val nextAttemptAt = timestampWithTimeZone("next_attempt_at")
  val sentAt = timestampWithTimeZone("sent_at").nullable()
  val lastError = text("last_error").nullable()
  override val primaryKey = PrimaryKey(id)
}

object NotificationPreferencesTable : Table("notification_preferences") {
  val userId = uuid("user_id").references(UsersTable.id)
  val notificationType = text("notification_type")
  val inAppEnabled = bool("in_app_enabled")
  val emailEnabled = bool("email_enabled")
  override val primaryKey = PrimaryKey(userId, notificationType)
}

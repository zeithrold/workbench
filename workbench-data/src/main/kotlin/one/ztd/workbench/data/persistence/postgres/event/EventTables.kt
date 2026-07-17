package one.ztd.workbench.data.persistence.postgres.event

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import one.ztd.workbench.data.persistence.postgres.config.inet
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object IssueEventsTable : Table("issue_events") {
  val id = uuid("id")
  val eventId = text("event_id").uniqueIndex()
  val tenantId = uuid("tenant_id")
  val projectId = uuid("project_id").nullable()
  val issueId = uuid("issue_id").nullable()
  val actorUserId = uuid("actor_user_id").nullable()
  val eventType = text("event_type")
  val occurredAt = timestampWithTimeZone("occurred_at")
  val payload = jsonb("payload", Json.Default, JsonElement.serializer())
  val requestId = text("request_id").nullable()
  val traceId = text("trace_id").nullable()
  override val primaryKey = PrimaryKey(id)
}

object AuditLogsTable : Table("audit_logs") {
  val id = uuid("id")
  val auditId = text("audit_id").uniqueIndex()
  val tenantId = uuid("tenant_id").nullable()
  val actorUserId = uuid("actor_user_id").nullable()
  val actorLoginAccountId = uuid("actor_login_account_id").nullable()
  val action = text("action")
  val resourceType = text("resource_type")
  val resourceId = uuid("resource_id").nullable()
  val resourceApiId = text("resource_api_id").nullable()
  val result = text("result")
  val reason = text("reason").nullable()
  val beforeSnapshot = jsonb("before_snapshot", Json.Default, JsonElement.serializer()).nullable()
  val afterSnapshot = jsonb("after_snapshot", Json.Default, JsonElement.serializer()).nullable()
  val metadata = jsonb("metadata", Json.Default, JsonElement.serializer())
  val ipAddress = inet("ip_address").nullable()
  val userAgent = text("user_agent").nullable()
  val requestId = text("request_id").nullable()
  val traceId = text("trace_id").nullable()
  val occurredAt = timestampWithTimeZone("occurred_at")
  override val primaryKey = PrimaryKey(id)
}

object AuthEventsTable : Table("auth_events") {
  val id = uuid("id")
  val authEventId = text("auth_event_id").uniqueIndex()
  val tenantId = uuid("tenant_id").nullable()
  val userId = uuid("user_id").nullable()
  val loginAccountId = uuid("login_account_id").nullable()
  val loginMethodId = uuid("login_method_id").nullable()
  val eventType = text("event_type")
  val result = text("result")
  val failureReason = text("failure_reason").nullable()
  val ipAddress = inet("ip_address").nullable()
  val userAgent = text("user_agent").nullable()
  val metadata = jsonb("metadata", Json.Default, JsonElement.serializer())
  val occurredAt = timestampWithTimeZone("occurred_at")
  override val primaryKey = PrimaryKey(id)
}

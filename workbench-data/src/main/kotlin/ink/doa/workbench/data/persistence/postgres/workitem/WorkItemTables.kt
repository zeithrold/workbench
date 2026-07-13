package ink.doa.workbench.data.persistence.postgres.workitem

import ink.doa.workbench.agile.workitem.richtext.RichTextDocument
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.persistence.postgres.identity.UsersTable
import ink.doa.workbench.data.persistence.postgres.project.ProjectsTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object SprintsTable : Table("sprints") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id)
  val name = text("name")
  val goal = text("goal").nullable()
  val status = text("status")
  val startAt = timestampWithTimeZone("start_at").nullable()
  val endAt = timestampWithTimeZone("end_at").nullable()
  val closedAt = timestampWithTimeZone("closed_at").nullable()
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val archivedAt = timestampWithTimeZone("archived_at").nullable()
  val archivedBy = uuid("archived_by").references(UsersTable.id).nullable()
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  val deletedBy = uuid("deleted_by").references(UsersTable.id).nullable()
  val deleteReason = text("delete_reason").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object SprintCloseOperationsTable : Table("sprint_close_operations") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id)
  val sprintId = uuid("sprint_id").references(SprintsTable.id)
  val targetSprintId = uuid("target_sprint_id").references(SprintsTable.id).nullable()
  val disposition = text("disposition")
  val requestedBy = uuid("requested_by").references(UsersTable.id)
  val status = text("status")
  val totalItems = integer("total_items")
  val processedItems = integer("processed_items")
  val failedItems = integer("failed_items")
  val lastError = text("last_error").nullable()
  val idempotencyKey = text("idempotency_key").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val startedAt = timestampWithTimeZone("started_at").nullable()
  val completedAt = timestampWithTimeZone("completed_at").nullable()
  override val primaryKey = PrimaryKey(id)
}

object IssuesTable : Table("issues") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id)
  val issueTypeId = uuid("issue_type_id").references(IssueTypesTable.id)
  val issueTypeConfigId = uuid("issue_type_config_id").references(IssueTypeConfigsTable.id)
  val sequenceNo = long("sequence_no")
  val title = text("title")
  val description =
    jsonb("description_document", Json.Default, RichTextDocument.serializer()).nullable()
  val descriptionPlainText = text("description_plain_text").nullable()
  val statusId = uuid("status_id").references(IssueStatusesTable.id)
  val priorityId = uuid("priority_id").references(PrioritiesTable.id).nullable()
  val reporterId = uuid("reporter_id").references(UsersTable.id)
  val assigneeId = uuid("assignee_id").references(UsersTable.id).nullable()
  val sprintId = uuid("sprint_id").references(SprintsTable.id).nullable()
  val propertiesSnapshot = jsonb("properties_snapshot", Json.Default, JsonElement.serializer())
  val archivedAt = timestampWithTimeZone("archived_at").nullable()
  val archivedBy = uuid("archived_by").references(UsersTable.id).nullable()
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  val deletedBy = uuid("deleted_by").references(UsersTable.id).nullable()
  val deleteReason = text("delete_reason").nullable()
  val createdBy = uuid("created_by").references(UsersTable.id)
  val updatedBy = uuid("updated_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object IssueHierarchyTable : Table("issue_hierarchy") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id)
  val parentIssueId = uuid("parent_issue_id").references(IssuesTable.id)
  val childIssueId = uuid("child_issue_id").references(IssuesTable.id).uniqueIndex()
  val rank = integer("rank")
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

object IssueKeyAliasesTable : Table("issue_key_aliases") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id)
  val issueId = uuid("issue_id").references(IssuesTable.id)
  val issueKey = text("issue_key")
  val projectIdentifier = text("project_identifier")
  val sequenceNo = long("sequence_no")
  val isCurrent = bool("is_current")
  val createdAt = timestampWithTimeZone("created_at")
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  override val primaryKey = PrimaryKey(id)
}

object IssuePropertyValuesTable : Table("issue_property_values") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val issueId = uuid("issue_id").references(IssuesTable.id)
  val propertyId = uuid("property_id").references(PropertyDefinitionsTable.id)
  val valueText = text("value_text").nullable()
  val valueNumber = decimal("value_number", 38, 10).nullable()
  val valueBoolean = bool("value_boolean").nullable()
  val valueDate = text("value_date").nullable()
  val valueDatetime = timestampWithTimeZone("value_datetime").nullable()
  val valueJson = jsonb("value_json", Json.Default, JsonElement.serializer()).nullable()
  val valueUserId = uuid("value_user_id").references(UsersTable.id).nullable()
  val valueProjectId = uuid("value_project_id").references(ProjectsTable.id).nullable()
  val valueIssueId = uuid("value_issue_id").references(IssuesTable.id).nullable()
  val valueOptionId = uuid("value_option_id").references(PropertyOptionsTable.id).nullable()
  val valueArray = jsonb("value_array", Json.Default, JsonElement.serializer()).nullable()
  val updatedBy = uuid("updated_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object IssueStatusHistoryTable : Table("issue_status_history") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val issueId = uuid("issue_id").references(IssuesTable.id)
  val fromStatusId = uuid("from_status_id").references(IssueStatusesTable.id).nullable()
  val toStatusId = uuid("to_status_id").references(IssueStatusesTable.id)
  val transitionId = uuid("transition_id").references(WorkflowTransitionsTable.id).nullable()
  val actorUserId = uuid("actor_user_id").references(UsersTable.id).nullable()
  val startedAt = timestampWithTimeZone("started_at").nullable()
  val endedAt = timestampWithTimeZone("ended_at").nullable()
  val changedAt = timestampWithTimeZone("changed_at")
  val metadata = jsonb("metadata", Json.Default, JsonElement.serializer())
  override val primaryKey = PrimaryKey(id)
}

object IssueSprintHistoryTable : Table("issue_sprint_history") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val issueId = uuid("issue_id").references(IssuesTable.id)
  val sprintId = uuid("sprint_id").references(SprintsTable.id)
  val addedBy = uuid("added_by").references(UsersTable.id).nullable()
  val addedAt = timestampWithTimeZone("added_at")
  val removedBy = uuid("removed_by").references(UsersTable.id).nullable()
  val removedAt = timestampWithTimeZone("removed_at").nullable()
  override val primaryKey = PrimaryKey(id)
}

object IssueCommentsTable : Table("issue_comments") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val issueId = uuid("issue_id").references(IssuesTable.id)
  val authorId = uuid("author_id").references(UsersTable.id)
  val body = jsonb("body_document", Json.Default, RichTextDocument.serializer())
  val bodyPlainText = text("body_plain_text").nullable()
  val transitionId = uuid("transition_id").references(WorkflowTransitionsTable.id).nullable()
  val statusHistoryId = uuid("status_history_id").references(IssueStatusHistoryTable.id).nullable()
  val editedAt = timestampWithTimeZone("edited_at").nullable()
  val archivedAt = timestampWithTimeZone("archived_at").nullable()
  val archivedBy = uuid("archived_by").references(UsersTable.id).nullable()
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  val deletedBy = uuid("deleted_by").references(UsersTable.id).nullable()
  val deleteReason = text("delete_reason").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object AttachmentsTable : Table("attachments") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val issueId = uuid("issue_id").references(IssuesTable.id).nullable()
  val commentId = uuid("comment_id").references(IssueCommentsTable.id).nullable()
  val uploadedBy = uuid("uploaded_by").references(UsersTable.id)
  val filename = text("filename")
  val contentType = text("content_type").nullable()
  val byteSize = long("byte_size")
  val checksum = text("checksum").nullable()
  val storageKey = text("storage_key").uniqueIndex()
  val purpose = text("purpose")
  val uploadStatus = text("upload_status")
  val metadata = jsonb("metadata", Json.Default, JsonElement.serializer())
  val archivedAt = timestampWithTimeZone("archived_at").nullable()
  val archivedBy = uuid("archived_by").references(UsersTable.id).nullable()
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  val deletedBy = uuid("deleted_by").references(UsersTable.id).nullable()
  val deleteReason = text("delete_reason").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

object WorkItemViewsTable : Table("work_item_views") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id).nullable()
  val ownerId = uuid("owner_id").references(UsersTable.id)
  val name = text("name")
  val description = text("description").nullable()
  val visibility = text("visibility")
  val queryAst = jsonb("query_ast", Json.Default, JsonElement.serializer())
  val displayFields = jsonb("display_fields", Json.Default, JsonElement.serializer())
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object WorkItemEventsTable : Table("work_item_events") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id)
  val workItemId = uuid("work_item_id").references(IssuesTable.id)
  val sequence = long("sequence")
  val eventType = text("event_type")
  val occurredAt = timestampWithTimeZone("occurred_at")
  val actorUserId = uuid("actor_user_id").references(UsersTable.id).nullable()
  val summary = text("summary").nullable()
  val payload = jsonb("payload", Json.Default, JsonElement.serializer())
  val sourceType = text("source_type")
  val sourceId = text("source_id").nullable()
  val correlationId = text("correlation_id").nullable()
  val requestId = text("request_id").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

object DomainOutboxTable : Table("domain_outbox") {
  val id = uuid("id")
  val eventId = text("event_id")
  val eventType = text("event_type")
  val eventVersion = integer("event_version")
  val topic = text("topic")
  val partitionKey = text("partition_key")
  val tenantId = text("tenant_id").nullable()
  val payload = jsonb("payload", Json.Default, JsonElement.serializer())
  val createdAt = timestampWithTimeZone("created_at")
  val retentionUntil = timestampWithTimeZone("retention_until")
  override val primaryKey = PrimaryKey(id)
}

object WorkItemTimelineEntriesTable : Table("work_item_timeline_entries") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id)
  val workItemId = uuid("work_item_id").references(IssuesTable.id)
  val eventId = uuid("event_id").references(WorkItemEventsTable.id)
  val sequence = long("sequence")
  val occurredAt = timestampWithTimeZone("occurred_at")
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

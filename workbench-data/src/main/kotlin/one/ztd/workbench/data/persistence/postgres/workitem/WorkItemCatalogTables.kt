package one.ztd.workbench.data.persistence.postgres.workitem

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import one.ztd.workbench.data.persistence.postgres.identity.TenantsTable
import one.ztd.workbench.data.persistence.postgres.identity.UsersTable
import one.ztd.workbench.data.persistence.postgres.project.ProjectsTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object PrioritiesTable : Table("priorities") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val code = text("code")
  val name = text("name")
  val rank = integer("rank")
  val color = text("color").nullable()
  val icon = text("icon").nullable()
  val isDefault = bool("is_default")
  val isActive = bool("is_active")
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object IssueStatusesTable : Table("issue_statuses") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val code = text("code")
  val name = text("name")
  val statusGroup = text("status_group")
  val rank = integer("rank")
  val color = text("color").nullable()
  val isTerminal = bool("is_terminal")
  val isActive = bool("is_active")
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object PropertyDefinitionsTable : Table("property_definitions") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val code = text("code")
  val name = text("name")
  val description = text("description").nullable()
  val dataType = text("data_type")
  val isSystem = bool("is_system")
  val isArray = bool("is_array")
  val validationSchema = jsonb("validation_schema", Json.Default, JsonElement.serializer())
  val searchConfig = jsonb("search_config", Json.Default, JsonElement.serializer())
  val isActive = bool("is_active")
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object PropertyOptionsTable : Table("property_options") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val propertyId = uuid("property_id").references(PropertyDefinitionsTable.id)
  val code = text("code")
  val label = text("label")
  val rank = integer("rank")
  val color = text("color").nullable()
  val isDefault = bool("is_default")
  val isActive = bool("is_active")
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object IssueTypesTable : Table("issue_types") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id).nullable()
  val scope = text("scope")
  val code = text("code")
  val name = text("name")
  val description = text("description").nullable()
  val icon = text("icon").nullable()
  val color = text("color").nullable()
  val rank = integer("rank")
  val isActive = bool("is_active")
  val archivedAt = timestampWithTimeZone("archived_at").nullable()
  val archivedBy = uuid("archived_by").references(UsersTable.id).nullable()
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  val deletedBy = uuid("deleted_by").references(UsersTable.id).nullable()
  val deleteReason = text("delete_reason").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object WorkflowsTable : Table("workflows") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val code = text("code")
  val name = text("name")
  val description = text("description").nullable()
  val version = integer("version")
  val isActive = bool("is_active")
  val publishedAt = timestampWithTimeZone("published_at").nullable()
  val archivedAt = timestampWithTimeZone("archived_at").nullable()
  val archivedBy = uuid("archived_by").references(UsersTable.id).nullable()
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  val deletedBy = uuid("deleted_by").references(UsersTable.id).nullable()
  val deleteReason = text("delete_reason").nullable()
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object IssueTypeConfigsTable : Table("issue_type_configs") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val scope = text("scope")
  val projectId = uuid("project_id").references(ProjectsTable.id).nullable()
  val issueTypeId = uuid("issue_type_id").references(IssueTypesTable.id)
  val workflowId = uuid("workflow_id").references(WorkflowsTable.id)
  val version = integer("version")
  val nameOverride = text("name_override").nullable()
  val iconOverride = text("icon_override").nullable()
  val colorOverride = text("color_override").nullable()
  val rank = integer("rank")
  val isActive = bool("is_active")
  val validFrom = timestampWithTimeZone("valid_from")
  val validTo = timestampWithTimeZone("valid_to").nullable()
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  val createFields = jsonb("create_fields", Json.Default, JsonElement.serializer())
  override val primaryKey = PrimaryKey(id)
}

object IssueTypeConfigStatusesTable : Table("issue_type_config_statuses") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val issueTypeConfigId = uuid("issue_type_config_id").references(IssueTypeConfigsTable.id)
  val statusId = uuid("status_id").references(IssueStatusesTable.id)
  val isInitial = bool("is_initial")
  val isTerminal = bool("is_terminal")
  val rank = integer("rank")
  override val primaryKey = PrimaryKey(id)
}

object IssueTypeConfigPropertiesTable : Table("issue_type_config_properties") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val issueTypeConfigId = uuid("issue_type_config_id").references(IssueTypeConfigsTable.id)
  val propertyId = uuid("property_id").references(PropertyDefinitionsTable.id)
  val validationOverride = jsonb("validation_override", Json.Default, JsonElement.serializer())
  val rank = integer("rank")
  val displayConfig = jsonb("display_config", Json.Default, JsonElement.serializer())
  override val primaryKey = PrimaryKey(id)
}

object IssueSubtypeConstraintsTable : Table("issue_subtype_constraints") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id).nullable()
  val parentIssueTypeId = uuid("parent_issue_type_id").references(IssueTypesTable.id)
  val childIssueTypeId = uuid("child_issue_type_id").references(IssueTypesTable.id)
  val isDefault = bool("is_default")
  val minChildren = integer("min_children").nullable()
  val maxChildren = integer("max_children").nullable()
  val isActive = bool("is_active")
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object WorkflowTransitionsTable : Table("workflow_transitions") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val workflowId = uuid("workflow_id").references(WorkflowsTable.id)
  val name = text("name")
  val fromStatusId = uuid("from_status_id").references(IssueStatusesTable.id).nullable()
  val toStatusId = uuid("to_status_id").references(IssueStatusesTable.id)
  val rank = integer("rank")
  val preconditionAst = jsonb("precondition_ast", Json.Default, JsonElement.serializer())
  val transitionFields = jsonb("fields", Json.Default, JsonElement.serializer())
  val isActive = bool("is_active")
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object IssueTypeConfigAccessRulesTable : Table("issue_type_config_access_rules") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val issueTypeConfigId = uuid("issue_type_config_id").references(IssueTypeConfigsTable.id)
  val subjectType = text("subject_type")
  val subjectUserId = uuid("subject_user_id").references(UsersTable.id).nullable()
  val subjectGroupId =
    uuid("subject_group_id")
      .references(one.ztd.workbench.data.persistence.postgres.permission.GroupsTable.id)
      .nullable()
  val subjectRoleCode = text("subject_role_code").nullable()
  val actionType = text("action_type")
  val transitionId = uuid("transition_id").references(WorkflowTransitionsTable.id).nullable()
  val fieldKey = text("field_key").nullable()
  val effect = text("effect")
  val conditionJson = jsonb("condition_json", Json.Default, JsonElement.serializer())
  val rank = integer("rank")
  val isActive = bool("is_active")
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

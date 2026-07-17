package one.ztd.workbench.data.persistence.postgres.permission

import one.ztd.workbench.data.persistence.postgres.identity.TenantsTable
import one.ztd.workbench.data.persistence.postgres.identity.UsersTable
import one.ztd.workbench.data.persistence.postgres.project.ProjectsTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object AdminUsersTable : Table("admin_users") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val userId = uuid("user_id").references(UsersTable.id)
  val scope = text("scope")
  val tenantId = uuid("tenant_id").references(TenantsTable.id).nullable()
  val status = text("status")
  val grantedBy = uuid("granted_by").references(UsersTable.id).nullable()
  val validFrom = timestampWithTimeZone("valid_from")
  val validTo = timestampWithTimeZone("valid_to").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object AccessGrantsTable : Table("access_grants") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val scope = text("scope")
  val tenantId = uuid("tenant_id").references(TenantsTable.id).nullable()
  val projectId = uuid("project_id").references(ProjectsTable.id).nullable()
  val subjectUserId = uuid("subject_user_id").references(UsersTable.id)
  val action = text("action").references(PermissionActionsTable.code)
  val resourcePattern = text("resource_pattern")
  val effect = text("effect")
  val validFrom = timestampWithTimeZone("valid_from")
  val validTo = timestampWithTimeZone("valid_to").nullable()
  val grantedBy = uuid("granted_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

object PermissionActionsTable : Table("permission_actions") {
  val id = uuid("id")
  val code = text("code").uniqueIndex()
  val description = text("description").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

object GroupsTable : Table("groups") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val code = text("code")
  val name = text("name")
  val description = text("description").nullable()
  val builtin = bool("builtin")
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  override val primaryKey = PrimaryKey(id)
}

object GroupMembersTable : Table("group_members") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val groupId = uuid("group_id").references(GroupsTable.id)
  val userId = uuid("user_id").references(UsersTable.id)
  val status = text("status")
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object PermissionPoliciesTable : Table("permission_policies") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val code = text("code")
  val name = text("name")
  val description = text("description").nullable()
  val builtin = bool("builtin")
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  val deletedAt = timestampWithTimeZone("deleted_at").nullable()
  override val primaryKey = PrimaryKey(id)
}

object PermissionPolicyRulesTable : Table("permission_policy_rules") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val policyId = uuid("policy_id").references(PermissionPoliciesTable.id)
  val action = text("action").references(PermissionActionsTable.code)
  val resourcePattern = text("resource_pattern")
  val effect = text("effect")
  val conditionJson = text("condition_json").nullable()
  val position = integer("position")
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

object PermissionBindingsTable : Table("permission_bindings") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id).nullable()
  val principalType = text("principal_type")
  val principalUserId = uuid("principal_user_id").references(UsersTable.id).nullable()
  val principalGroupId = uuid("principal_group_id").references(GroupsTable.id).nullable()
  val policyId = uuid("policy_id").references(PermissionPoliciesTable.id)
  val validFrom = timestampWithTimeZone("valid_from")
  val validTo = timestampWithTimeZone("valid_to").nullable()
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

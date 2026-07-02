package doa.ink.workbench.data.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object RolesTable : Table("roles") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id).nullable()
  val scope = text("scope")
  val code = text("code")
  val name = text("name")
  val description = text("description").nullable()
  val isBuiltin = bool("is_builtin")
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

object PermissionActionsTable : Table("permission_actions") {
  val id = uuid("id")
  val code = text("code").uniqueIndex()
  val description = text("description").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

object PermissionPoliciesTable : Table("permission_policies") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val roleId = uuid("role_id").references(RolesTable.id)
  val actionId = uuid("action_id").references(PermissionActionsTable.id)
  val effect = text("effect")
  val resourcePattern = text("resource_pattern")
  val conditionAst = jsonb("condition_ast", Json.Default, JsonElement.serializer())
  val version = integer("version")
  val validFrom = timestampWithTimeZone("valid_from")
  val validTo = timestampWithTimeZone("valid_to").nullable()
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

object RoleAssignmentsTable : Table("role_assignments") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val userId = uuid("user_id").references(UsersTable.id)
  val roleId = uuid("role_id").references(RolesTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id).nullable()
  val grantedBy = uuid("granted_by").references(UsersTable.id).nullable()
  val validFrom = timestampWithTimeZone("valid_from")
  val validTo = timestampWithTimeZone("valid_to").nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

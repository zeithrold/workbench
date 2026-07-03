package doa.ink.workbench.data.persistence

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

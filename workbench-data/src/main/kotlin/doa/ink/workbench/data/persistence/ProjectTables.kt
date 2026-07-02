package doa.ink.workbench.data.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object ProjectsTable : Table("projects") {
  val id = uuid("id")
  val apiId = text("api_id").uniqueIndex()
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val name = text("name")
  val identifier = text("identifier")
  val description = text("description").nullable()
  val leadUserId = uuid("lead_user_id").references(UsersTable.id).nullable()
  val nextIssueSequence = long("next_issue_sequence")
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

object ProjectIdentifierAliasesTable : Table("project_identifier_aliases") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id)
  val identifier = text("identifier")
  val isCurrent = bool("is_current")
  val validFrom = timestampWithTimeZone("valid_from")
  val validTo = timestampWithTimeZone("valid_to").nullable()
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  override val primaryKey = PrimaryKey(id)
}

object IssueHierarchyPoliciesTable : Table("issue_hierarchy_policies") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val projectId = uuid("project_id").references(ProjectsTable.id).uniqueIndex()
  val maxDepth = integer("max_depth")
  val allowCrossProjectChildren = bool("allow_cross_project_children")
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

package ink.doa.workbench.data.persistence.postgres.tenantconfig

import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.persistence.postgres.identity.UsersTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object TenantConfigEntriesTable : Table("tenant_config_entries") {
  val id = uuid("id")
  val tenantId = uuid("tenant_id").references(TenantsTable.id)
  val key = text("config_key")
  val value = jsonb("value_json", Json.Default, JsonElement.serializer())
  val secretRef = text("secret_ref").nullable()
  val createdBy = uuid("created_by").references(UsersTable.id).nullable()
  val updatedBy = uuid("updated_by").references(UsersTable.id).nullable()
  val createdAt = timestampWithTimeZone("created_at")
  val updatedAt = timestampWithTimeZone("updated_at")
  override val primaryKey = PrimaryKey(id)
}

package doa.ink.workbench.data.permission

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.permission.AssignRoleCommand
import doa.ink.workbench.core.permission.CreatePermissionActionCommand
import doa.ink.workbench.core.permission.CreatePermissionPolicyCommand
import doa.ink.workbench.core.permission.CreateRoleCommand
import doa.ink.workbench.core.permission.PermissionActionRecord
import doa.ink.workbench.core.permission.PermissionActionRepository
import doa.ink.workbench.core.permission.PermissionPolicyRecord
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.RoleAssignmentRecord
import doa.ink.workbench.core.permission.RoleAssignmentRepository
import doa.ink.workbench.core.permission.RoleRecord
import doa.ink.workbench.core.permission.RoleRepository
import doa.ink.workbench.core.permission.RoleScope
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.PermissionCondition
import doa.ink.workbench.core.permission.model.PermissionEffect
import doa.ink.workbench.data.persistence.PermissionActionsTable
import doa.ink.workbench.data.persistence.PermissionPoliciesTable
import doa.ink.workbench.data.persistence.RoleAssignmentsTable
import doa.ink.workbench.data.persistence.RolesTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedRoleRepository(private val database: Database) : RoleRepository {
  override suspend fun create(command: CreateRoleCommand): RoleRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("rol")
      val now = nowUtc()
      RolesTable.insert {
        it[RolesTable.id] = id.toKotlinUuid()
        it[RolesTable.apiId] = apiId.value
        it[tenantId] = command.tenantId?.toKotlinUuid()
        it[scope] = command.scope.dbValue
        it[code] = command.code
        it[name] = command.name
        it[description] = command.description
        it[isBuiltin] = command.isBuiltin
        it[createdAt] = now
        it[updatedAt] = now
      }
      RolesTable.selectAll().where { RolesTable.id eq id.toKotlinUuid() }.single().toRoleRecord()
    }

  override suspend fun findById(id: UUID): RoleRecord? =
    suspendTransaction(db = database) {
      RolesTable.selectAll()
        .where { RolesTable.id eq id.toKotlinUuid() }
        .singleOrNull()
        ?.toRoleRecord()
    }

  override suspend fun findByCode(tenantId: UUID?, code: String): RoleRecord? =
    suspendTransaction(db = database) {
      RolesTable.selectAll()
        .where {
          (RolesTable.code eq code) and
            if (tenantId == null) RolesTable.tenantId.isNull()
            else RolesTable.tenantId eq tenantId.toKotlinUuid()
        }
        .singleOrNull()
        ?.toRoleRecord()
    }

  override suspend fun list(tenantId: UUID?): List<RoleRecord> =
    suspendTransaction(db = database) {
      RolesTable.selectAll()
        .where {
          if (tenantId == null) RolesTable.tenantId.isNull()
          else (RolesTable.tenantId eq tenantId.toKotlinUuid()) or RolesTable.tenantId.isNull()
        }
        .orderBy(RolesTable.code to SortOrder.ASC)
        .map { it.toRoleRecord() }
    }
}

@Repository
class ExposedPermissionActionRepository(private val database: Database) :
  PermissionActionRepository {
  override suspend fun upsert(command: CreatePermissionActionCommand): PermissionActionRecord =
    suspendTransaction(db = database) {
      val existing =
        PermissionActionsTable.selectAll()
          .where { PermissionActionsTable.code eq command.code.code }
          .singleOrNull()
      if (existing != null) {
        existing.toPermissionActionRecord()
      } else {
        val id = UUID.randomUUID()
        val now = nowUtc()
        PermissionActionsTable.insert {
          it[PermissionActionsTable.id] = id.toKotlinUuid()
          it[code] = command.code.code
          it[description] = command.description
          it[createdAt] = now
        }
        PermissionActionsTable.selectAll()
          .where { PermissionActionsTable.id eq id.toKotlinUuid() }
          .single()
          .toPermissionActionRecord()
      }
    }

  override suspend fun findByCode(code: AuthorizationAction): PermissionActionRecord? =
    suspendTransaction(db = database) {
      PermissionActionsTable.selectAll()
        .where { PermissionActionsTable.code eq code.code }
        .singleOrNull()
        ?.toPermissionActionRecord()
    }

  override suspend fun list(): List<PermissionActionRecord> =
    suspendTransaction(db = database) {
      PermissionActionsTable.selectAll().orderBy(PermissionActionsTable.code to SortOrder.ASC).map {
        it.toPermissionActionRecord()
      }
    }
}

@Repository
class ExposedPermissionPolicyRepository(private val database: Database) :
  PermissionPolicyRepository {
  override suspend fun create(command: CreatePermissionPolicyCommand): PermissionPolicyRecord =
    suspendTransaction(db = database) {
      val actionId =
        PermissionActionsTable.selectAll()
          .where { PermissionActionsTable.code eq command.action.code }
          .singleOrNull()
          ?.get(PermissionActionsTable.id)
          ?: error("Permission action ${command.action.code} must exist before creating policy.")
      val id = UUID.randomUUID()
      val apiId = PublicId.new("pol")
      val now = nowUtc()
      PermissionPoliciesTable.insert {
        it[PermissionPoliciesTable.id] = id.toKotlinUuid()
        it[PermissionPoliciesTable.apiId] = apiId.value
        it[tenantId] = command.tenantId.toKotlinUuid()
        it[roleId] = command.roleId.toKotlinUuid()
        it[PermissionPoliciesTable.actionId] = actionId
        it[effect] = command.effect.dbValue
        it[resourcePattern] = command.resourcePattern
        it[conditionAst] = command.condition.toJson()
        it[version] = 1
        it[validFrom] = command.validFrom
        it[validTo] = command.validTo
        it[createdBy] = command.createdBy?.toKotlinUuid()
        it[createdAt] = now
      }
      selectPolicies()
        .where { PermissionPoliciesTable.id eq id.toKotlinUuid() }
        .single()
        .toPermissionPolicyRecord()
    }

  override suspend fun listByTenant(tenantId: UUID): List<PermissionPolicyRecord> =
    suspendTransaction(db = database) {
      selectPolicies()
        .where { PermissionPoliciesTable.tenantId eq tenantId.toKotlinUuid() }
        .orderBy(PermissionPoliciesTable.createdAt to SortOrder.DESC)
        .map { it.toPermissionPolicyRecord() }
    }

  override suspend fun listActiveByRoles(
    tenantId: UUID,
    roleIds: Collection<UUID>,
    at: OffsetDateTime,
  ): List<PermissionPolicyRecord> =
    if (roleIds.isEmpty()) {
      emptyList()
    } else {
      suspendTransaction(db = database) {
        selectPolicies()
          .where {
            (PermissionPoliciesTable.tenantId eq tenantId.toKotlinUuid()) and
              (PermissionPoliciesTable.roleId inList roleIds.map { it.toKotlinUuid() }) and
              (PermissionPoliciesTable.validFrom lessEq at) and
              (PermissionPoliciesTable.validTo.isNull() or
                (PermissionPoliciesTable.validTo greater at))
          }
          .map { it.toPermissionPolicyRecord() }
      }
    }

  override suspend fun expire(id: UUID, validTo: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      PermissionPoliciesTable.update({ PermissionPoliciesTable.id eq id.toKotlinUuid() }) {
        it[PermissionPoliciesTable.validTo] = validTo
      } > 0
    }
}

@Repository
class ExposedRoleAssignmentRepository(private val database: Database) : RoleAssignmentRepository {
  override suspend fun assign(command: AssignRoleCommand): RoleAssignmentRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("ras")
      val now = nowUtc()
      RoleAssignmentsTable.insert {
        it[RoleAssignmentsTable.id] = id.toKotlinUuid()
        it[RoleAssignmentsTable.apiId] = apiId.value
        it[tenantId] = command.tenantId.toKotlinUuid()
        it[userId] = command.userId.toKotlinUuid()
        it[roleId] = command.roleId.toKotlinUuid()
        it[projectId] = command.projectId?.toKotlinUuid()
        it[grantedBy] = command.grantedBy?.toKotlinUuid()
        it[validFrom] = command.validFrom
        it[validTo] = command.validTo
        it[createdAt] = now
      }
      RoleAssignmentsTable.selectAll()
        .where { RoleAssignmentsTable.id eq id.toKotlinUuid() }
        .single()
        .toRoleAssignmentRecord()
    }

  override suspend fun listByTenant(tenantId: UUID): List<RoleAssignmentRecord> =
    suspendTransaction(db = database) {
      RoleAssignmentsTable.selectAll()
        .where { RoleAssignmentsTable.tenantId eq tenantId.toKotlinUuid() }
        .orderBy(RoleAssignmentsTable.createdAt to SortOrder.DESC)
        .map { it.toRoleAssignmentRecord() }
    }

  override suspend fun listActiveByUser(
    tenantId: UUID,
    userId: UUID,
    projectId: UUID?,
    at: OffsetDateTime,
  ): List<RoleAssignmentRecord> =
    suspendTransaction(db = database) {
      val projectScope =
        if (projectId == null) {
          RoleAssignmentsTable.projectId.isNull()
        } else {
          RoleAssignmentsTable.projectId.isNull() or
            (RoleAssignmentsTable.projectId eq projectId.toKotlinUuid())
        }
      RoleAssignmentsTable.selectAll()
        .where {
          (RoleAssignmentsTable.tenantId eq tenantId.toKotlinUuid()) and
            (RoleAssignmentsTable.userId eq userId.toKotlinUuid()) and
            projectScope and
            (RoleAssignmentsTable.validFrom lessEq at) and
            (RoleAssignmentsTable.validTo.isNull() or (RoleAssignmentsTable.validTo greater at))
        }
        .map { it.toRoleAssignmentRecord() }
    }

  override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      RoleAssignmentsTable.update({ RoleAssignmentsTable.id eq id.toKotlinUuid() }) {
        it[validTo] = revokedAt
      } > 0
    }
}

private fun selectPolicies() = PermissionPoliciesTable.innerJoin(PermissionActionsTable).selectAll()

private val PermissionEffect.dbValue: String
  get() = name.lowercase()

private fun ResultRow.toRoleRecord() =
  RoleRecord(
    id = this[RolesTable.id].toJavaUuid(),
    apiId = PublicId(this[RolesTable.apiId]),
    tenantId = this[RolesTable.tenantId]?.toJavaUuid(),
    scope = roleScopeOf(this[RolesTable.scope]),
    code = this[RolesTable.code],
    name = this[RolesTable.name],
    description = this[RolesTable.description],
    isBuiltin = this[RolesTable.isBuiltin],
    createdAt = this[RolesTable.createdAt],
    updatedAt = this[RolesTable.updatedAt],
  )

private fun ResultRow.toPermissionActionRecord() =
  PermissionActionRecord(
    id = this[PermissionActionsTable.id].toJavaUuid(),
    code = AuthorizationAction(this[PermissionActionsTable.code]),
    description = this[PermissionActionsTable.description],
    createdAt = this[PermissionActionsTable.createdAt],
  )

private fun ResultRow.toPermissionPolicyRecord() =
  PermissionPolicyRecord(
    id = this[PermissionPoliciesTable.id].toJavaUuid(),
    apiId = PublicId(this[PermissionPoliciesTable.apiId]),
    tenantId = this[PermissionPoliciesTable.tenantId].toJavaUuid(),
    roleId = this[PermissionPoliciesTable.roleId].toJavaUuid(),
    action = AuthorizationAction(this[PermissionActionsTable.code]),
    effect = permissionEffectOf(this[PermissionPoliciesTable.effect]),
    resourcePattern = this[PermissionPoliciesTable.resourcePattern],
    condition = this[PermissionPoliciesTable.conditionAst].toPermissionCondition(),
    version = this[PermissionPoliciesTable.version],
    validFrom = this[PermissionPoliciesTable.validFrom],
    validTo = this[PermissionPoliciesTable.validTo],
    createdBy = this[PermissionPoliciesTable.createdBy]?.toJavaUuid(),
    createdAt = this[PermissionPoliciesTable.createdAt],
  )

private fun ResultRow.toRoleAssignmentRecord() =
  RoleAssignmentRecord(
    id = this[RoleAssignmentsTable.id].toJavaUuid(),
    apiId = PublicId(this[RoleAssignmentsTable.apiId]),
    tenantId = this[RoleAssignmentsTable.tenantId].toJavaUuid(),
    userId = this[RoleAssignmentsTable.userId].toJavaUuid(),
    roleId = this[RoleAssignmentsTable.roleId].toJavaUuid(),
    projectId = this[RoleAssignmentsTable.projectId]?.toJavaUuid(),
    grantedBy = this[RoleAssignmentsTable.grantedBy]?.toJavaUuid(),
    validFrom = this[RoleAssignmentsTable.validFrom],
    validTo = this[RoleAssignmentsTable.validTo],
    createdAt = this[RoleAssignmentsTable.createdAt],
  )

private fun roleScopeOf(value: String): RoleScope = RoleScope.entries.first { it.dbValue == value }

private fun permissionEffectOf(value: String): PermissionEffect =
  when (value.lowercase()) {
    "allow" -> PermissionEffect.ALLOW
    "deny" -> PermissionEffect.DENY
    else -> error("Unknown permission effect: $value")
  }

private fun PermissionCondition?.toJson(): JsonElement =
  when (this) {
    null -> JsonObject(emptyMap())
    is PermissionCondition.FieldEquals ->
      JsonObject(
        mapOf(
          "type" to JsonPrimitive("field_equals"),
          "field" to JsonPrimitive(field),
          "expected" to JsonPrimitive(expected),
        )
      )
    is PermissionCondition.AllOf ->
      JsonObject(
        mapOf(
          "type" to JsonPrimitive("all_of"),
          "conditions" to JsonArray(conditions.map { it.toJson() }),
        )
      )
  }

@Suppress("ReturnCount")
private fun JsonElement.toPermissionCondition(): PermissionCondition? {
  val obj = this as? JsonObject ?: return null
  val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
  return when (type) {
    "field_equals" ->
      PermissionCondition.FieldEquals(
        field = obj["field"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        expected = obj["expected"]?.jsonPrimitive?.contentOrNull.orEmpty(),
      )
    "all_of" ->
      PermissionCondition.AllOf(
        conditions =
          (obj["conditions"] as? JsonArray)?.mapNotNull { it.toPermissionCondition() }
            ?: emptyList()
      )
    else -> null
  }
}

private fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

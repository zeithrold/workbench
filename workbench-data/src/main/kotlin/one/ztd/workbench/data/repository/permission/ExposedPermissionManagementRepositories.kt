package one.ztd.workbench.data.repository.permission

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import one.ztd.workbench.data.persistence.postgres.permission.GroupMembersTable
import one.ztd.workbench.data.persistence.postgres.permission.GroupsTable
import one.ztd.workbench.data.persistence.postgres.permission.PermissionBindingsTable
import one.ztd.workbench.data.persistence.postgres.permission.PermissionPoliciesTable
import one.ztd.workbench.data.persistence.postgres.permission.PermissionPolicyRulesTable
import one.ztd.workbench.identity.permission.AddGroupMemberCommand
import one.ztd.workbench.identity.permission.CreatePermissionBindingCommand
import one.ztd.workbench.identity.permission.CreatePermissionGroupCommand
import one.ztd.workbench.identity.permission.CreatePermissionPolicyCommand
import one.ztd.workbench.identity.permission.CreatePermissionPolicyRuleCommand
import one.ztd.workbench.identity.permission.GroupMemberRecord
import one.ztd.workbench.identity.permission.GroupMemberStatus
import one.ztd.workbench.identity.permission.PermissionBindingRecord
import one.ztd.workbench.identity.permission.PermissionBindingRepository
import one.ztd.workbench.identity.permission.PermissionGroupRecord
import one.ztd.workbench.identity.permission.PermissionGroupRepository
import one.ztd.workbench.identity.permission.PermissionPolicyRecord
import one.ztd.workbench.identity.permission.PermissionPolicyRepository
import one.ztd.workbench.identity.permission.PermissionPolicyRuleRecord
import one.ztd.workbench.identity.permission.PermissionPrincipalType
import one.ztd.workbench.identity.permission.ReplacePermissionPolicyCommand
import one.ztd.workbench.identity.permission.ResolvedPermissionRule
import one.ztd.workbench.identity.permission.UpdatePermissionGroupCommand
import one.ztd.workbench.identity.permission.UpdatePermissionPolicyCommand
import one.ztd.workbench.kernel.common.ids.PublicId
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedPermissionGroupRepository(private val database: Database) : PermissionGroupRepository {
  override suspend fun create(command: CreatePermissionGroupCommand): PermissionGroupRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("grp")
      val now = AdminRepositoryMappers.nowUtc()
      GroupsTable.insert {
        it[GroupsTable.id] = id.toKotlinUuid()
        it[GroupsTable.apiId] = apiId.value
        it[tenantId] = command.tenantId.toKotlinUuid()
        it[code] = command.code
        it[name] = command.name
        it[description] = command.description
        it[builtin] = command.builtin
        it[createdAt] = now
        it[updatedAt] = now
      }
      GroupsTable.selectAll()
        .where { GroupsTable.id eq id.toKotlinUuid() }
        .single()
        .toPermissionGroupRecord()
    }

  override suspend fun update(command: UpdatePermissionGroupCommand): PermissionGroupRecord =
    suspendTransaction(db = database) {
      val now = AdminRepositoryMappers.nowUtc()
      GroupsTable.update({ GroupsTable.id eq command.groupId.toKotlinUuid() }) {
        command.name?.let { name -> it[GroupsTable.name] = name }
        command.description?.let { description -> it[GroupsTable.description] = description }
        it[updatedAt] = now
      }
      GroupsTable.selectAll()
        .where { GroupsTable.id eq command.groupId.toKotlinUuid() }
        .single()
        .toPermissionGroupRecord()
    }

  override suspend fun findById(tenantId: UUID, id: UUID): PermissionGroupRecord? =
    suspendTransaction(db = database) {
      GroupsTable.selectAll()
        .where { activeGroup(tenantId) and (GroupsTable.id eq id.toKotlinUuid()) }
        .singleOrNull()
        ?.toPermissionGroupRecord()
    }

  override suspend fun findByApiId(tenantId: UUID, apiId: String): PermissionGroupRecord? =
    suspendTransaction(db = database) {
      GroupsTable.selectAll()
        .where { activeGroup(tenantId) and (GroupsTable.apiId eq apiId) }
        .singleOrNull()
        ?.toPermissionGroupRecord()
    }

  override suspend fun findByCode(tenantId: UUID, code: String): PermissionGroupRecord? =
    suspendTransaction(db = database) {
      GroupsTable.selectAll()
        .where { activeGroup(tenantId) and (GroupsTable.code eq code) }
        .singleOrNull()
        ?.toPermissionGroupRecord()
    }

  override suspend fun list(tenantId: UUID): List<PermissionGroupRecord> =
    suspendTransaction(db = database) {
      GroupsTable.selectAll()
        .where { activeGroup(tenantId) }
        .orderBy(GroupsTable.builtin to SortOrder.DESC, GroupsTable.name to SortOrder.ASC)
        .map { it.toPermissionGroupRecord() }
    }

  override suspend fun delete(tenantId: UUID, id: UUID): Boolean =
    suspendTransaction(db = database) {
      GroupsTable.update({ activeGroup(tenantId) and (GroupsTable.id eq id.toKotlinUuid()) }) {
        val now = AdminRepositoryMappers.nowUtc()
        it[deletedAt] = now
        it[updatedAt] = now
      } > 0
    }

  override suspend fun addMember(command: AddGroupMemberCommand): GroupMemberRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("gmb")
      val now = AdminRepositoryMappers.nowUtc()
      GroupMembersTable.insert {
        it[GroupMembersTable.id] = id.toKotlinUuid()
        it[GroupMembersTable.apiId] = apiId.value
        it[groupId] = command.groupId.toKotlinUuid()
        it[userId] = command.userId.toKotlinUuid()
        it[status] = GroupMemberStatus.ACTIVE.dbValue
        it[createdAt] = now
        it[updatedAt] = now
      }
      GroupMembersTable.selectAll()
        .where { GroupMembersTable.id eq id.toKotlinUuid() }
        .single()
        .toGroupMemberRecord()
    }

  override suspend fun removeMember(
    groupId: UUID,
    userId: UUID,
    removedAt: OffsetDateTime,
  ): Boolean =
    suspendTransaction(db = database) {
      GroupMembersTable.update({
        (GroupMembersTable.groupId eq groupId.toKotlinUuid()) and
          (GroupMembersTable.userId eq userId.toKotlinUuid()) and
          (GroupMembersTable.status eq GroupMemberStatus.ACTIVE.dbValue)
      }) {
        it[status] = GroupMemberStatus.REMOVED.dbValue
        it[updatedAt] = removedAt
      } > 0
    }

  override suspend fun listMembers(groupId: UUID): List<GroupMemberRecord> =
    suspendTransaction(db = database) {
      GroupMembersTable.selectAll()
        .where {
          (GroupMembersTable.groupId eq groupId.toKotlinUuid()) and
            (GroupMembersTable.status eq GroupMemberStatus.ACTIVE.dbValue)
        }
        .orderBy(GroupMembersTable.createdAt to SortOrder.ASC)
        .map { it.toGroupMemberRecord() }
    }

  override suspend fun listActiveGroupIdsForUser(tenantId: UUID, userId: UUID): Set<UUID> =
    suspendTransaction(db = database) {
      val activeGroupIds =
        GroupsTable.selectAll().where { activeGroup(tenantId) }.map { it[GroupsTable.id] }
      if (activeGroupIds.isEmpty()) {
        emptySet()
      } else {
        GroupMembersTable.selectAll()
          .where {
            (GroupMembersTable.userId eq userId.toKotlinUuid()) and
              (GroupMembersTable.status eq GroupMemberStatus.ACTIVE.dbValue) and
              (GroupMembersTable.groupId inList activeGroupIds)
          }
          .map { it[GroupMembersTable.groupId].toJavaUuid() }
          .toSet()
      }
    }

  private fun activeGroup(tenantId: UUID): Op<Boolean> =
    (GroupsTable.tenantId eq tenantId.toKotlinUuid()) and GroupsTable.deletedAt.isNull()
}

@Repository
class ExposedPermissionPolicyRepository(private val database: Database) :
  PermissionPolicyRepository {
  override suspend fun create(command: CreatePermissionPolicyCommand): PermissionPolicyRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("pol")
      val now = AdminRepositoryMappers.nowUtc()
      PermissionPoliciesTable.insert {
        it[PermissionPoliciesTable.id] = id.toKotlinUuid()
        it[PermissionPoliciesTable.apiId] = apiId.value
        it[tenantId] = command.tenantId.toKotlinUuid()
        it[code] = command.code
        it[name] = command.name
        it[description] = command.description
        it[builtin] = command.builtin
        it[createdAt] = now
        it[updatedAt] = now
      }
      command.rules.forEach { rule ->
        PermissionPolicyRulesTable.insert {
          it[PermissionPolicyRulesTable.id] = UUID.randomUUID().toKotlinUuid()
          it[PermissionPolicyRulesTable.apiId] = PublicId.new("prl").value
          it[policyId] = id.toKotlinUuid()
          it[action] = rule.action.code
          it[resourcePattern] = rule.resourcePattern
          it[effect] = rule.effect.dbValue
          it[conditionJson] = rule.conditionJson
          it[position] = rule.position
          it[createdAt] = now
        }
      }
      PermissionPoliciesTable.selectAll()
        .where { PermissionPoliciesTable.id eq id.toKotlinUuid() }
        .single()
        .toPermissionPolicyRecord()
    }

  override suspend fun findById(tenantId: UUID, id: UUID): PermissionPolicyRecord? =
    suspendTransaction(db = database) {
      PermissionPoliciesTable.selectAll()
        .where { activePolicy(tenantId) and (PermissionPoliciesTable.id eq id.toKotlinUuid()) }
        .singleOrNull()
        ?.toPermissionPolicyRecord()
    }

  override suspend fun findByApiId(tenantId: UUID, apiId: String): PermissionPolicyRecord? =
    suspendTransaction(db = database) {
      PermissionPoliciesTable.selectAll()
        .where { activePolicy(tenantId) and (PermissionPoliciesTable.apiId eq apiId) }
        .singleOrNull()
        ?.toPermissionPolicyRecord()
    }

  override suspend fun findByCode(tenantId: UUID, code: String): PermissionPolicyRecord? =
    suspendTransaction(db = database) {
      PermissionPoliciesTable.selectAll()
        .where { activePolicy(tenantId) and (PermissionPoliciesTable.code eq code) }
        .singleOrNull()
        ?.toPermissionPolicyRecord()
    }

  override suspend fun list(tenantId: UUID): List<PermissionPolicyRecord> =
    suspendTransaction(db = database) {
      PermissionPoliciesTable.selectAll()
        .where { activePolicy(tenantId) }
        .orderBy(
          PermissionPoliciesTable.builtin to SortOrder.DESC,
          PermissionPoliciesTable.name to SortOrder.ASC,
        )
        .map { it.toPermissionPolicyRecord() }
    }

  override suspend fun addRule(
    command: CreatePermissionPolicyRuleCommand
  ): PermissionPolicyRuleRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("prl")
      val now = AdminRepositoryMappers.nowUtc()
      PermissionPolicyRulesTable.insert {
        it[PermissionPolicyRulesTable.id] = id.toKotlinUuid()
        it[PermissionPolicyRulesTable.apiId] = apiId.value
        it[policyId] = command.policyId.toKotlinUuid()
        it[action] = command.action.code
        it[resourcePattern] = command.resourcePattern
        it[effect] = command.effect.dbValue
        it[conditionJson] = command.conditionJson
        it[position] = command.position
        it[createdAt] = now
      }
      PermissionPolicyRulesTable.selectAll()
        .where { PermissionPolicyRulesTable.id eq id.toKotlinUuid() }
        .single()
        .toPermissionPolicyRuleRecord()
    }

  override suspend fun listRules(policyId: UUID): List<PermissionPolicyRuleRecord> =
    suspendTransaction(db = database) {
      PermissionPolicyRulesTable.selectAll()
        .where { PermissionPolicyRulesTable.policyId eq policyId.toKotlinUuid() }
        .orderBy(PermissionPolicyRulesTable.position to SortOrder.ASC)
        .map { it.toPermissionPolicyRuleRecord() }
    }

  override suspend fun replace(command: ReplacePermissionPolicyCommand): PermissionPolicyRecord? =
    suspendTransaction(db = database) {
      val now = AdminRepositoryMappers.nowUtc()
      val updated =
        PermissionPoliciesTable.update({
          (PermissionPoliciesTable.id eq command.policyId.toKotlinUuid()) and
            (PermissionPoliciesTable.updatedAt eq command.expectedUpdatedAt)
        }) {
          it[name] = command.name
          it[description] = command.description
          it[updatedAt] = now
        }
      if (updated == 0) return@suspendTransaction null

      val existing =
        PermissionPolicyRulesTable.selectAll()
          .where { PermissionPolicyRulesTable.policyId eq command.policyId.toKotlinUuid() }
          .associateBy { it[PermissionPolicyRulesTable.apiId] }
      val retainedIds = command.rules.mapNotNull { it.apiId }.toSet()
      PermissionPolicyRulesTable.deleteWhere {
        (policyId eq command.policyId.toKotlinUuid()) and
          if (retainedIds.isEmpty()) apiId neq "" else apiId notInList retainedIds
      }
      command.rules.forEach { rule ->
        val existingRule = rule.apiId?.let(existing::get)
        if (existingRule == null) {
          PermissionPolicyRulesTable.insert {
            it[id] = UUID.randomUUID().toKotlinUuid()
            it[apiId] = PublicId.new("prl").value
            it[policyId] = command.policyId.toKotlinUuid()
            it[action] = rule.action.code
            it[resourcePattern] = rule.resourcePattern
            it[effect] = rule.effect.dbValue
            it[conditionJson] = rule.conditionJson
            it[position] = rule.position
            it[createdAt] = now
          }
        } else {
          PermissionPolicyRulesTable.update({
            PermissionPolicyRulesTable.id eq existingRule[PermissionPolicyRulesTable.id]
          }) {
            it[action] = rule.action.code
            it[resourcePattern] = rule.resourcePattern
            it[effect] = rule.effect.dbValue
            it[conditionJson] = rule.conditionJson
            it[position] = rule.position
          }
        }
      }
      PermissionPoliciesTable.selectAll()
        .where { PermissionPoliciesTable.id eq command.policyId.toKotlinUuid() }
        .single()
        .toPermissionPolicyRecord()
    }

  override suspend fun update(command: UpdatePermissionPolicyCommand): PermissionPolicyRecord =
    suspendTransaction(db = database) {
      val now = AdminRepositoryMappers.nowUtc()
      PermissionPoliciesTable.update({
        PermissionPoliciesTable.id eq command.policyId.toKotlinUuid()
      }) {
        command.name?.let { name -> it[PermissionPoliciesTable.name] = name }
        command.description?.let { description ->
          it[PermissionPoliciesTable.description] = description
        }
        it[updatedAt] = now
      }
      PermissionPoliciesTable.selectAll()
        .where { PermissionPoliciesTable.id eq command.policyId.toKotlinUuid() }
        .single()
        .toPermissionPolicyRecord()
    }

  override suspend fun delete(tenantId: UUID, id: UUID): Boolean =
    suspendTransaction(db = database) {
      PermissionPoliciesTable.update({
        activePolicy(tenantId) and (PermissionPoliciesTable.id eq id.toKotlinUuid())
      }) {
        val now = AdminRepositoryMappers.nowUtc()
        it[deletedAt] = now
        it[updatedAt] = now
      } > 0
    }

  override suspend fun hasActiveBindings(policyId: UUID, at: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      PermissionBindingsTable.selectAll()
        .where {
          (PermissionBindingsTable.policyId eq policyId.toKotlinUuid()) and
            (PermissionBindingsTable.validFrom lessEq at) and
            (PermissionBindingsTable.validTo.isNull() or
              (PermissionBindingsTable.validTo greater at))
        }
        .any()
    }

  private fun activePolicy(tenantId: UUID): Op<Boolean> =
    (PermissionPoliciesTable.tenantId eq tenantId.toKotlinUuid()) and
      PermissionPoliciesTable.deletedAt.isNull()
}

@Repository
class ExposedPermissionBindingRepository(private val database: Database) :
  PermissionBindingRepository {
  override suspend fun create(command: CreatePermissionBindingCommand): PermissionBindingRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("pbd")
      val now = AdminRepositoryMappers.nowUtc()
      PermissionBindingsTable.insert {
        it[PermissionBindingsTable.id] = id.toKotlinUuid()
        it[PermissionBindingsTable.apiId] = apiId.value
        it[tenantId] = command.tenantId.toKotlinUuid()
        it[projectId] = command.projectId?.toKotlinUuid()
        it[principalType] = command.principalType.dbValue
        it[principalUserId] = command.principalUserId?.toKotlinUuid()
        it[principalGroupId] = command.principalGroupId?.toKotlinUuid()
        it[policyId] = command.policyId.toKotlinUuid()
        it[validFrom] = command.validFrom
        it[validTo] = command.validTo
        it[createdBy] = command.createdBy?.toKotlinUuid()
        it[createdAt] = now
      }
      PermissionBindingsTable.selectAll()
        .where { PermissionBindingsTable.id eq id.toKotlinUuid() }
        .single()
        .toPermissionBindingRecord()
    }

  override suspend fun findByApiId(tenantId: UUID, apiId: String): PermissionBindingRecord? =
    suspendTransaction(db = database) {
      PermissionBindingsTable.selectAll()
        .where {
          (PermissionBindingsTable.tenantId eq tenantId.toKotlinUuid()) and
            (PermissionBindingsTable.apiId eq apiId)
        }
        .singleOrNull()
        ?.toPermissionBindingRecord()
    }

  override suspend fun listByTenant(tenantId: UUID): List<PermissionBindingRecord> =
    suspendTransaction(db = database) {
      PermissionBindingsTable.selectAll()
        .where { PermissionBindingsTable.tenantId eq tenantId.toKotlinUuid() }
        .orderBy(PermissionBindingsTable.createdAt to SortOrder.DESC)
        .map { it.toPermissionBindingRecord() }
    }

  override suspend fun listByProject(
    tenantId: UUID,
    projectId: UUID,
  ): List<PermissionBindingRecord> =
    suspendTransaction(db = database) {
      PermissionBindingsTable.selectAll()
        .where {
          (PermissionBindingsTable.tenantId eq tenantId.toKotlinUuid()) and
            (PermissionBindingsTable.projectId eq projectId.toKotlinUuid())
        }
        .orderBy(PermissionBindingsTable.createdAt to SortOrder.DESC)
        .map { it.toPermissionBindingRecord() }
    }

  override suspend fun listProjectIdsForSubject(
    tenantId: UUID,
    subjectUserId: UUID,
    at: OffsetDateTime,
  ): Set<UUID> =
    suspendTransaction(db = database) {
      val activeGroupIds =
        GroupMembersTable.selectAll()
          .where {
            (GroupMembersTable.userId eq subjectUserId.toKotlinUuid()) and
              (GroupMembersTable.status eq GroupMemberStatus.ACTIVE.dbValue)
          }
          .map { it[GroupMembersTable.groupId] }
      val principalFilter =
        (PermissionBindingsTable.principalType eq PermissionPrincipalType.USER.dbValue) and
          (PermissionBindingsTable.principalUserId eq subjectUserId.toKotlinUuid()) or
          if (activeGroupIds.isEmpty()) {
            Op.FALSE
          } else {
            (PermissionBindingsTable.principalType eq PermissionPrincipalType.GROUP.dbValue) and
              (PermissionBindingsTable.principalGroupId inList activeGroupIds)
          }
      PermissionBindingsTable.selectAll()
        .where {
          (PermissionBindingsTable.tenantId eq tenantId.toKotlinUuid()) and
            (PermissionBindingsTable.projectId neq null) and
            principalFilter and
            (PermissionBindingsTable.validFrom lessEq at) and
            (PermissionBindingsTable.validTo.isNull() or
              (PermissionBindingsTable.validTo greater at))
        }
        .mapNotNull { it[PermissionBindingsTable.projectId]?.toJavaUuid() }
        .toSet()
    }

  override suspend fun expire(tenantId: UUID, id: UUID, validTo: OffsetDateTime): Boolean =
    suspendTransaction(db = database) {
      PermissionBindingsTable.update({
        (PermissionBindingsTable.tenantId eq tenantId.toKotlinUuid()) and
          (PermissionBindingsTable.id eq id.toKotlinUuid()) and
          PermissionBindingsTable.validTo.isNull()
      }) {
        it[PermissionBindingsTable.validTo] = validTo
      } > 0
    }

  override suspend fun expireByProject(
    tenantId: UUID,
    projectId: UUID,
    validTo: OffsetDateTime,
  ): Int =
    suspendTransaction(db = database) {
      PermissionBindingsTable.update({
        (PermissionBindingsTable.tenantId eq tenantId.toKotlinUuid()) and
          (PermissionBindingsTable.projectId eq projectId.toKotlinUuid()) and
          PermissionBindingsTable.validTo.isNull()
      }) {
        it[PermissionBindingsTable.validTo] = validTo
      }
    }

  override suspend fun listActiveRulesForSubject(
    subjectUserId: UUID,
    tenantId: UUID,
    projectId: UUID?,
    at: OffsetDateTime,
  ): List<ResolvedPermissionRule> =
    suspendTransaction(db = database) {
      val activeGroupIds =
        GroupMembersTable.selectAll()
          .where {
            (GroupMembersTable.userId eq subjectUserId.toKotlinUuid()) and
              (GroupMembersTable.status eq GroupMemberStatus.ACTIVE.dbValue)
          }
          .map { it[GroupMembersTable.groupId] }
      val principalFilter = permissionBindingPrincipalFilter(subjectUserId, activeGroupIds)
      val projectFilter =
        PermissionBindingsTable.projectId.isNull() or
          (projectId?.let { PermissionBindingsTable.projectId eq it.toKotlinUuid() } ?: Op.FALSE)
      val activePolicyIds =
        PermissionBindingsTable.selectAll()
          .where {
            (PermissionBindingsTable.tenantId eq tenantId.toKotlinUuid()) and
              projectFilter and
              principalFilter and
              (PermissionBindingsTable.validFrom lessEq at) and
              (PermissionBindingsTable.validTo.isNull() or
                (PermissionBindingsTable.validTo greater at))
          }
          .map {
            it[PermissionBindingsTable.id].toJavaUuid() to it[PermissionBindingsTable.policyId]
          }
      if (activePolicyIds.isEmpty()) {
        emptyList()
      } else {
        PermissionPolicyRulesTable.selectAll()
          .where { PermissionPolicyRulesTable.policyId inList activePolicyIds.map { it.second } }
          .flatMap { ruleRow ->
            val policyId = ruleRow[PermissionPolicyRulesTable.policyId]
            activePolicyIds
              .filter { it.second == policyId }
              .map { (bindingId, _) ->
                ResolvedPermissionRule(
                  bindingId = bindingId,
                  action =
                    one.ztd.workbench.identity.permission.model.AuthorizationAction(
                      ruleRow[PermissionPolicyRulesTable.action]
                    ),
                  resourcePattern = ruleRow[PermissionPolicyRulesTable.resourcePattern],
                  effect = permissionEffectOf(ruleRow[PermissionPolicyRulesTable.effect]),
                  conditionJson = ruleRow[PermissionPolicyRulesTable.conditionJson],
                )
              }
          }
      }
    }
}

private fun permissionBindingPrincipalFilter(
  subjectUserId: UUID,
  activeGroupIds: List<kotlin.uuid.Uuid>,
): Op<Boolean> =
  (PermissionBindingsTable.principalType eq PermissionPrincipalType.USER.dbValue) and
    (PermissionBindingsTable.principalUserId eq subjectUserId.toKotlinUuid()) or
    ((PermissionBindingsTable.principalType eq PermissionPrincipalType.TENANT_MEMBER.dbValue) and
      PermissionBindingsTable.principalUserId.isNull() and
      PermissionBindingsTable.principalGroupId.isNull()) or
    if (activeGroupIds.isEmpty()) {
      Op.FALSE
    } else {
      (PermissionBindingsTable.principalType eq PermissionPrincipalType.GROUP.dbValue) and
        (PermissionBindingsTable.principalGroupId inList activeGroupIds)
    }

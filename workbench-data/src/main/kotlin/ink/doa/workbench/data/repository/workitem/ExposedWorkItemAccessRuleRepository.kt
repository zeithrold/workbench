package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.access.CreateWorkItemAccessRuleCommand
import ink.doa.workbench.core.workitem.access.WorkItemAccessActionType
import ink.doa.workbench.core.workitem.access.WorkItemAccessRuleRecord
import ink.doa.workbench.core.workitem.access.WorkItemAccessRuleRepository
import ink.doa.workbench.core.workitem.access.WorkItemAccessSubjectType
import ink.doa.workbench.data.persistence.postgres.workitem.IssueTypeConfigAccessRulesTable
import ink.doa.workbench.data.persistence.postgres.workitem.asObject
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedWorkItemAccessRuleRepository(private val database: Database) :
  WorkItemAccessRuleRepository {
  override suspend fun create(command: CreateWorkItemAccessRuleCommand): WorkItemAccessRuleRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("war")
      val now = nowUtc()
      IssueTypeConfigAccessRulesTable.insert {
        it[IssueTypeConfigAccessRulesTable.id] = id.toKotlinUuid()
        it[IssueTypeConfigAccessRulesTable.apiId] = apiId.value
        it[tenantId] = command.tenantId.toKotlinUuid()
        it[issueTypeConfigId] = command.issueTypeConfigId.toKotlinUuid()
        it[subjectType] = command.subjectType.dbValue
        it[subjectUserId] = command.subjectUserId?.toKotlinUuid()
        it[subjectGroupId] = command.subjectGroupId?.toKotlinUuid()
        it[subjectRoleCode] = command.subjectRoleCode
        it[actionType] = command.actionType.dbValue
        it[transitionId] = command.transitionId?.toKotlinUuid()
        it[fieldKey] = command.fieldKey
        it[effect] = command.effect.name.lowercase()
        it[conditionJson] = command.condition
        it[rank] = command.rank
        it[isActive] = true
        it[createdAt] = now
        it[updatedAt] = now
      }
      IssueTypeConfigAccessRulesTable.selectAll()
        .where { IssueTypeConfigAccessRulesTable.id eq id.toKotlinUuid() }
        .single()
        .toWorkItemAccessRuleRecord()
    }

  override suspend fun listByConfig(
    tenantId: UUID,
    issueTypeConfigId: UUID,
  ): List<WorkItemAccessRuleRecord> =
    suspendTransaction(db = database) {
      IssueTypeConfigAccessRulesTable.selectAll()
        .where {
          (IssueTypeConfigAccessRulesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueTypeConfigAccessRulesTable.issueTypeConfigId eq
              issueTypeConfigId.toKotlinUuid()) and
            (IssueTypeConfigAccessRulesTable.isActive eq true)
        }
        .orderBy(IssueTypeConfigAccessRulesTable.rank to SortOrder.ASC)
        .map { it.toWorkItemAccessRuleRecord() }
    }

  override suspend fun findByApiId(tenantId: UUID, apiId: String): WorkItemAccessRuleRecord? =
    suspendTransaction(db = database) {
      IssueTypeConfigAccessRulesTable.selectAll()
        .where {
          (IssueTypeConfigAccessRulesTable.tenantId eq tenantId.toKotlinUuid()) and
            (IssueTypeConfigAccessRulesTable.apiId eq apiId) and
            (IssueTypeConfigAccessRulesTable.isActive eq true)
        }
        .singleOrNull()
        ?.toWorkItemAccessRuleRecord()
    }

  override suspend fun deactivate(tenantId: UUID, ruleId: UUID): Boolean =
    suspendTransaction(db = database) {
      val now = nowUtc()
      IssueTypeConfigAccessRulesTable.update({
        (IssueTypeConfigAccessRulesTable.tenantId eq tenantId.toKotlinUuid()) and
          (IssueTypeConfigAccessRulesTable.id eq ruleId.toKotlinUuid()) and
          (IssueTypeConfigAccessRulesTable.isActive eq true)
      }) {
        it[isActive] = false
        it[updatedAt] = now
      } > 0
    }
}

private fun org.jetbrains.exposed.v1.core.ResultRow.toWorkItemAccessRuleRecord():
  WorkItemAccessRuleRecord =
  WorkItemAccessRuleRecord(
    id = this[IssueTypeConfigAccessRulesTable.id].toJavaUuid(),
    apiId = PublicId(this[IssueTypeConfigAccessRulesTable.apiId]),
    tenantId = this[IssueTypeConfigAccessRulesTable.tenantId].toJavaUuid(),
    issueTypeConfigId = this[IssueTypeConfigAccessRulesTable.issueTypeConfigId].toJavaUuid(),
    subjectType =
      WorkItemAccessSubjectType.fromDbValue(this[IssueTypeConfigAccessRulesTable.subjectType]),
    subjectUserId = this[IssueTypeConfigAccessRulesTable.subjectUserId]?.toJavaUuid(),
    subjectGroupId = this[IssueTypeConfigAccessRulesTable.subjectGroupId]?.toJavaUuid(),
    subjectRoleCode = this[IssueTypeConfigAccessRulesTable.subjectRoleCode],
    actionType =
      WorkItemAccessActionType.fromDbValue(this[IssueTypeConfigAccessRulesTable.actionType]),
    transitionId = this[IssueTypeConfigAccessRulesTable.transitionId]?.toJavaUuid(),
    fieldKey = this[IssueTypeConfigAccessRulesTable.fieldKey],
    effect = permissionEffectOf(this[IssueTypeConfigAccessRulesTable.effect]),
    condition = this[IssueTypeConfigAccessRulesTable.conditionJson].asObject(),
    rank = this[IssueTypeConfigAccessRulesTable.rank],
    isActive = this[IssueTypeConfigAccessRulesTable.isActive],
    createdAt = this[IssueTypeConfigAccessRulesTable.createdAt],
    updatedAt = this[IssueTypeConfigAccessRulesTable.updatedAt],
  )

private fun permissionEffectOf(value: String): PermissionEffect =
  when (value.lowercase()) {
    "allow" -> PermissionEffect.ALLOW
    "deny" -> PermissionEffect.DENY
    else -> error("Unknown permission effect: $value")
  }

private fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

package doa.ink.workbench.data.tenant

import doa.ink.workbench.core.permission.AdminUserStatus
import doa.ink.workbench.core.tenant.TenantDestructionRepository
import doa.ink.workbench.data.persistence.AccessGrantsTable
import doa.ink.workbench.data.persistence.AdminUsersTable
import doa.ink.workbench.data.persistence.AttachmentsTable
import doa.ink.workbench.data.persistence.AuthSessionsTable
import doa.ink.workbench.data.persistence.BearerTokensTable
import doa.ink.workbench.data.persistence.InvitationsTable
import doa.ink.workbench.data.persistence.IssueCommentsTable
import doa.ink.workbench.data.persistence.IssueHierarchyPoliciesTable
import doa.ink.workbench.data.persistence.IssueHierarchyTable
import doa.ink.workbench.data.persistence.IssueKeyAliasesTable
import doa.ink.workbench.data.persistence.IssuePropertyValuesTable
import doa.ink.workbench.data.persistence.IssueSprintHistoryTable
import doa.ink.workbench.data.persistence.IssueStatusHistoryTable
import doa.ink.workbench.data.persistence.IssueStatusesTable
import doa.ink.workbench.data.persistence.IssueSubtypeConstraintsTable
import doa.ink.workbench.data.persistence.IssueTypeConfigPropertiesTable
import doa.ink.workbench.data.persistence.IssueTypeConfigStatusesTable
import doa.ink.workbench.data.persistence.IssueTypeConfigsTable
import doa.ink.workbench.data.persistence.IssueTypesTable
import doa.ink.workbench.data.persistence.IssuesTable
import doa.ink.workbench.data.persistence.PrioritiesTable
import doa.ink.workbench.data.persistence.ProjectIdentifierAliasesTable
import doa.ink.workbench.data.persistence.ProjectsTable
import doa.ink.workbench.data.persistence.PropertyDefinitionsTable
import doa.ink.workbench.data.persistence.PropertyOptionsTable
import doa.ink.workbench.data.persistence.SavedFiltersTable
import doa.ink.workbench.data.persistence.SprintsTable
import doa.ink.workbench.data.persistence.TenantConfigEntriesTable
import doa.ink.workbench.data.persistence.TenantLoginMethodSettingsTable
import doa.ink.workbench.data.persistence.TenantMembersTable
import doa.ink.workbench.data.persistence.WorkflowTransitionsTable
import doa.ink.workbench.data.persistence.WorkflowsTable
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedTenantDestructionRepository(private val database: Database) :
  TenantDestructionRepository {
  override suspend fun revokeSessionsByActiveTenant(
    tenantId: UUID,
    revokedAt: OffsetDateTime,
  ): Int =
    suspendTransaction(db = database) {
      AuthSessionsTable.update({
        (AuthSessionsTable.activeTenantId eq tenantId.toKotlinUuid()) and
          AuthSessionsTable.revokedAt.isNull()
      }) {
        it[AuthSessionsTable.revokedAt] = revokedAt
        it[AuthSessionsTable.updatedAt] = revokedAt
      }
    }

  override suspend fun revokeBearerTokensByTenant(
    tenantId: UUID,
    revokedAt: OffsetDateTime,
  ): Int =
    suspendTransaction(db = database) {
      BearerTokensTable.update({
        (BearerTokensTable.tenantId eq tenantId.toKotlinUuid()) and
          BearerTokensTable.revokedAt.isNull()
      }) {
        it[BearerTokensTable.revokedAt] = revokedAt
        it[BearerTokensTable.updatedAt] = revokedAt
      }
    }

  override suspend fun revokeAdminUsersByTenant(
    tenantId: UUID,
    revokedAt: OffsetDateTime,
  ): Int =
    suspendTransaction(db = database) {
      AdminUsersTable.update({
        (AdminUsersTable.tenantId eq tenantId.toKotlinUuid()) and
          (AdminUsersTable.status eq AdminUserStatus.ACTIVE.dbValue)
      }) {
        it[AdminUsersTable.status] = AdminUserStatus.REVOKED.dbValue
        it[AdminUsersTable.validTo] = revokedAt
        it[AdminUsersTable.updatedAt] = revokedAt
      }
    }

  override suspend fun expireAccessGrantsByTenant(
    tenantId: UUID,
    expiredAt: OffsetDateTime,
  ): Int =
    suspendTransaction(db = database) {
      AccessGrantsTable.update({
        (AccessGrantsTable.tenantId eq tenantId.toKotlinUuid()) and
          AccessGrantsTable.validTo.isNull()
      }) {
        it[AccessGrantsTable.validTo] = expiredAt
      }
    }

  override suspend fun cancelPendingInvitationsByTenant(
    tenantId: UUID,
    cancelledAt: OffsetDateTime,
  ): Int =
    suspendTransaction(db = database) {
      InvitationsTable.update({
        (InvitationsTable.tenantId eq tenantId.toKotlinUuid()) and
          InvitationsTable.consumedAt.isNull()
      }) {
        it[InvitationsTable.consumedAt] = cancelledAt
      }
    }

  override suspend fun softDeleteTenantScopedData(
    tenantId: UUID,
    deletedAt: OffsetDateTime,
    deletedBy: UUID,
    deleteReason: String?,
  ): Unit =
    suspendTransaction(db = database) {
      val tenantUuid = tenantId.toKotlinUuid()
      val deletedByUuid = deletedBy.toKotlinUuid()
      softDeleteRowsWithDeletedAt(tenantUuid, deletedAt, deletedByUuid, deleteReason)
      hardDeleteRowsWithoutDeletedAt(tenantUuid)
    }

  @Suppress("LongMethod")
  private fun softDeleteRowsWithDeletedAt(
    tenantUuid: kotlin.uuid.Uuid,
    deletedAt: OffsetDateTime,
    deletedByUuid: kotlin.uuid.Uuid,
    deleteReason: String?,
  ) {
    fun softDelete(
      table: org.jetbrains.exposed.v1.core.Table,
      tenantColumn: org.jetbrains.exposed.v1.core.Column<kotlin.uuid.Uuid>,
      deletedAtColumn: org.jetbrains.exposed.v1.core.Column<OffsetDateTime?>,
      updatedAtColumn: org.jetbrains.exposed.v1.core.Column<OffsetDateTime>? = null,
      deletedByColumn: org.jetbrains.exposed.v1.core.Column<kotlin.uuid.Uuid?>? = null,
      deleteReasonColumn: org.jetbrains.exposed.v1.core.Column<String?>? = null,
    ) {
      table.update({ (tenantColumn eq tenantUuid) and deletedAtColumn.isNull() }) { row ->
        row[deletedAtColumn] = deletedAt
        deletedByColumn?.let { row[it] = deletedByUuid }
        deleteReasonColumn?.let { row[it] = deleteReason }
        updatedAtColumn?.let { row[it] = deletedAt }
      }
    }

    softDelete(
      AttachmentsTable,
      AttachmentsTable.tenantId,
      AttachmentsTable.deletedAt,
      deletedByColumn = AttachmentsTable.deletedBy,
      deleteReasonColumn = AttachmentsTable.deleteReason,
    )
    softDelete(
      IssueCommentsTable,
      IssueCommentsTable.tenantId,
      IssueCommentsTable.deletedAt,
      IssueCommentsTable.updatedAt,
      IssueCommentsTable.deletedBy,
      IssueCommentsTable.deleteReason,
    )
    softDelete(
      IssuesTable,
      IssuesTable.tenantId,
      IssuesTable.deletedAt,
      IssuesTable.updatedAt,
      IssuesTable.deletedBy,
      IssuesTable.deleteReason,
    )
    softDelete(
      SprintsTable,
      SprintsTable.tenantId,
      SprintsTable.deletedAt,
      SprintsTable.updatedAt,
      SprintsTable.deletedBy,
      SprintsTable.deleteReason,
    )
    softDelete(
      IssueTypesTable,
      IssueTypesTable.tenantId,
      IssueTypesTable.deletedAt,
      IssueTypesTable.updatedAt,
      IssueTypesTable.deletedBy,
      IssueTypesTable.deleteReason,
    )
    softDelete(
      WorkflowsTable,
      WorkflowsTable.tenantId,
      WorkflowsTable.deletedAt,
      WorkflowsTable.updatedAt,
      WorkflowsTable.deletedBy,
      WorkflowsTable.deleteReason,
    )
    softDelete(
      ProjectsTable,
      ProjectsTable.tenantId,
      ProjectsTable.deletedAt,
      ProjectsTable.updatedAt,
      ProjectsTable.deletedBy,
      ProjectsTable.deleteReason,
    )
    softDelete(
      TenantMembersTable,
      TenantMembersTable.tenantId,
      TenantMembersTable.deletedAt,
      TenantMembersTable.updatedAt,
      TenantMembersTable.deletedBy,
      TenantMembersTable.deleteReason,
    )
  }

  private fun hardDeleteRowsWithoutDeletedAt(tenantUuid: kotlin.uuid.Uuid) {
    IssueHierarchyTable.deleteWhere { IssueHierarchyTable.tenantId eq tenantUuid }
    IssueKeyAliasesTable.deleteWhere { IssueKeyAliasesTable.tenantId eq tenantUuid }
    IssuePropertyValuesTable.deleteWhere { IssuePropertyValuesTable.tenantId eq tenantUuid }
    IssueStatusHistoryTable.deleteWhere { IssueStatusHistoryTable.tenantId eq tenantUuid }
    IssueSprintHistoryTable.deleteWhere { IssueSprintHistoryTable.tenantId eq tenantUuid }
    SavedFiltersTable.deleteWhere { SavedFiltersTable.tenantId eq tenantUuid }
    PrioritiesTable.deleteWhere { PrioritiesTable.tenantId eq tenantUuid }
    IssueStatusesTable.deleteWhere { IssueStatusesTable.tenantId eq tenantUuid }
    PropertyOptionsTable.deleteWhere { PropertyOptionsTable.tenantId eq tenantUuid }
    PropertyDefinitionsTable.deleteWhere { PropertyDefinitionsTable.tenantId eq tenantUuid }
    IssueTypeConfigStatusesTable.deleteWhere {
      IssueTypeConfigStatusesTable.tenantId eq tenantUuid
    }
    IssueTypeConfigPropertiesTable.deleteWhere {
      IssueTypeConfigPropertiesTable.tenantId eq tenantUuid
    }
    IssueSubtypeConstraintsTable.deleteWhere {
      IssueSubtypeConstraintsTable.tenantId eq tenantUuid
    }
    WorkflowTransitionsTable.deleteWhere { WorkflowTransitionsTable.tenantId eq tenantUuid }
    IssueTypeConfigsTable.deleteWhere { IssueTypeConfigsTable.tenantId eq tenantUuid }
    ProjectIdentifierAliasesTable.deleteWhere {
      ProjectIdentifierAliasesTable.tenantId eq tenantUuid
    }
    IssueHierarchyPoliciesTable.deleteWhere {
      IssueHierarchyPoliciesTable.tenantId eq tenantUuid
    }
    TenantConfigEntriesTable.deleteWhere { TenantConfigEntriesTable.tenantId eq tenantUuid }
    TenantLoginMethodSettingsTable.deleteWhere {
      TenantLoginMethodSettingsTable.tenantId eq tenantUuid
    }
  }
}

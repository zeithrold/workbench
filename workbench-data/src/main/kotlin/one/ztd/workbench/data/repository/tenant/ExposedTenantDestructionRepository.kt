package one.ztd.workbench.data.repository.tenant

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import one.ztd.workbench.data.persistence.postgres.identity.AuthSessionsTable
import one.ztd.workbench.data.persistence.postgres.identity.BearerTokensTable
import one.ztd.workbench.data.persistence.postgres.identity.InvitationsTable
import one.ztd.workbench.data.persistence.postgres.identity.TenantLoginMethodSettingsTable
import one.ztd.workbench.data.persistence.postgres.identity.TenantMembersTable
import one.ztd.workbench.data.persistence.postgres.permission.AccessGrantsTable
import one.ztd.workbench.data.persistence.postgres.permission.AdminUsersTable
import one.ztd.workbench.data.persistence.postgres.project.IssueHierarchyPoliciesTable
import one.ztd.workbench.data.persistence.postgres.project.ProjectIdentifierAliasesTable
import one.ztd.workbench.data.persistence.postgres.project.ProjectsTable
import one.ztd.workbench.data.persistence.postgres.tenantconfig.TenantConfigEntriesTable
import one.ztd.workbench.data.persistence.postgres.workitem.AttachmentsTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueCommentsTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueHierarchyTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueKeyAliasesTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssuePropertyValuesTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueSprintHistoryTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueStatusHistoryTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueStatusesTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueSubtypeConstraintsTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueTypeConfigPropertiesTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueTypeConfigStatusesTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueTypeConfigsTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssueTypesTable
import one.ztd.workbench.data.persistence.postgres.workitem.IssuesTable
import one.ztd.workbench.data.persistence.postgres.workitem.PrioritiesTable
import one.ztd.workbench.data.persistence.postgres.workitem.PropertyDefinitionsTable
import one.ztd.workbench.data.persistence.postgres.workitem.PropertyOptionsTable
import one.ztd.workbench.data.persistence.postgres.workitem.SprintsTable
import one.ztd.workbench.data.persistence.postgres.workitem.WorkItemViewsTable
import one.ztd.workbench.data.persistence.postgres.workitem.WorkflowTransitionsTable
import one.ztd.workbench.data.persistence.postgres.workitem.WorkflowsTable
import one.ztd.workbench.identity.permission.AdminUserStatus
import one.ztd.workbench.tenant.tenant.TenantDestructionRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

private data class SoftDeleteContext(
  val tenantUuid: kotlin.uuid.Uuid,
  val deletedAt: OffsetDateTime,
  val deletedByUuid: kotlin.uuid.Uuid,
  val deleteReason: String?,
)

private data class SoftDeleteAuditColumns(
  val updatedAt: org.jetbrains.exposed.v1.core.Column<OffsetDateTime>? = null,
  val deletedBy: org.jetbrains.exposed.v1.core.Column<kotlin.uuid.Uuid?>? = null,
  val deleteReason: org.jetbrains.exposed.v1.core.Column<String?>? = null,
)

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

  private fun softDeleteRowsWithDeletedAt(
    tenantUuid: kotlin.uuid.Uuid,
    deletedAt: OffsetDateTime,
    deletedByUuid: kotlin.uuid.Uuid,
    deleteReason: String?,
  ) {
    softDeleteWorkItemRows(tenantUuid, deletedAt, deletedByUuid, deleteReason)
    softDeleteTenantResourceRows(tenantUuid, deletedAt, deletedByUuid, deleteReason)
  }

  private fun softDeleteWorkItemRows(
    tenantUuid: kotlin.uuid.Uuid,
    deletedAt: OffsetDateTime,
    deletedByUuid: kotlin.uuid.Uuid,
    deleteReason: String?,
  ) {
    val context = SoftDeleteContext(tenantUuid, deletedAt, deletedByUuid, deleteReason)
    softDelete(
      AttachmentsTable,
      AttachmentsTable.tenantId,
      AttachmentsTable.deletedAt,
      context,
      SoftDeleteAuditColumns(
        deletedBy = AttachmentsTable.deletedBy,
        deleteReason = AttachmentsTable.deleteReason,
      ),
    )
    softDelete(
      IssueCommentsTable,
      IssueCommentsTable.tenantId,
      IssueCommentsTable.deletedAt,
      context,
      SoftDeleteAuditColumns(
        IssueCommentsTable.updatedAt,
        IssueCommentsTable.deletedBy,
        IssueCommentsTable.deleteReason,
      ),
    )
    softDelete(
      IssuesTable,
      IssuesTable.tenantId,
      IssuesTable.deletedAt,
      context,
      SoftDeleteAuditColumns(
        IssuesTable.updatedAt,
        IssuesTable.deletedBy,
        IssuesTable.deleteReason,
      ),
    )
    softDelete(
      SprintsTable,
      SprintsTable.tenantId,
      SprintsTable.deletedAt,
      context,
      SoftDeleteAuditColumns(
        SprintsTable.updatedAt,
        SprintsTable.deletedBy,
        SprintsTable.deleteReason,
      ),
    )
  }

  private fun softDeleteTenantResourceRows(
    tenantUuid: kotlin.uuid.Uuid,
    deletedAt: OffsetDateTime,
    deletedByUuid: kotlin.uuid.Uuid,
    deleteReason: String?,
  ) {
    val context = SoftDeleteContext(tenantUuid, deletedAt, deletedByUuid, deleteReason)
    softDelete(
      IssueTypesTable,
      IssueTypesTable.tenantId,
      IssueTypesTable.deletedAt,
      context,
      SoftDeleteAuditColumns(
        IssueTypesTable.updatedAt,
        IssueTypesTable.deletedBy,
        IssueTypesTable.deleteReason,
      ),
    )
    softDelete(
      WorkflowsTable,
      WorkflowsTable.tenantId,
      WorkflowsTable.deletedAt,
      context,
      SoftDeleteAuditColumns(
        WorkflowsTable.updatedAt,
        WorkflowsTable.deletedBy,
        WorkflowsTable.deleteReason,
      ),
    )
    softDelete(
      ProjectsTable,
      ProjectsTable.tenantId,
      ProjectsTable.deletedAt,
      context,
      SoftDeleteAuditColumns(
        ProjectsTable.updatedAt,
        ProjectsTable.deletedBy,
        ProjectsTable.deleteReason,
      ),
    )
    softDelete(
      TenantMembersTable,
      TenantMembersTable.tenantId,
      TenantMembersTable.deletedAt,
      context,
      SoftDeleteAuditColumns(
        TenantMembersTable.updatedAt,
        TenantMembersTable.deletedBy,
        TenantMembersTable.deleteReason,
      ),
    )
  }

  private fun softDelete(
    table: org.jetbrains.exposed.v1.core.Table,
    tenantColumn: org.jetbrains.exposed.v1.core.Column<kotlin.uuid.Uuid>,
    deletedAtColumn: org.jetbrains.exposed.v1.core.Column<OffsetDateTime?>,
    context: SoftDeleteContext,
    auditColumns: SoftDeleteAuditColumns,
  ) {
    table.update({ (tenantColumn eq context.tenantUuid) and deletedAtColumn.isNull() }) { row ->
      row[deletedAtColumn] = context.deletedAt
      auditColumns.deletedBy?.let { row[it] = context.deletedByUuid }
      auditColumns.deleteReason?.let { row[it] = context.deleteReason }
      auditColumns.updatedAt?.let { row[it] = context.deletedAt }
    }
  }

  private fun hardDeleteRowsWithoutDeletedAt(tenantUuid: kotlin.uuid.Uuid) {
    IssueHierarchyTable.deleteWhere { IssueHierarchyTable.tenantId eq tenantUuid }
    IssueKeyAliasesTable.deleteWhere { IssueKeyAliasesTable.tenantId eq tenantUuid }
    IssuePropertyValuesTable.deleteWhere { IssuePropertyValuesTable.tenantId eq tenantUuid }
    IssueStatusHistoryTable.deleteWhere { IssueStatusHistoryTable.tenantId eq tenantUuid }
    IssueSprintHistoryTable.deleteWhere { IssueSprintHistoryTable.tenantId eq tenantUuid }
    WorkItemViewsTable.deleteWhere { WorkItemViewsTable.tenantId eq tenantUuid }
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

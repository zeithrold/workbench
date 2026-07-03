package ink.doa.workbench.data.project

import ink.doa.workbench.core.project.ProjectDestructionRepository
import ink.doa.workbench.data.persistence.IssueHierarchyTable
import ink.doa.workbench.data.persistence.IssuesTable
import ink.doa.workbench.data.persistence.PermissionBindingsTable
import ink.doa.workbench.data.persistence.ProjectIdentifierAliasesTable
import ink.doa.workbench.data.persistence.SprintsTable
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
class ExposedProjectDestructionRepository(private val database: Database) :
  ProjectDestructionRepository {
  override suspend fun expireBindingsByProject(
    tenantId: UUID,
    projectId: UUID,
    expiredAt: OffsetDateTime,
  ): Int =
    suspendTransaction(db = database) {
      PermissionBindingsTable.update({
        (PermissionBindingsTable.tenantId eq tenantId.toKotlinUuid()) and
          (PermissionBindingsTable.projectId eq projectId.toKotlinUuid()) and
          PermissionBindingsTable.validTo.isNull()
      }) {
        it[PermissionBindingsTable.validTo] = expiredAt
      }
    }

  override suspend fun softDeleteProjectScopedData(
    tenantId: UUID,
    projectId: UUID,
    deletedAt: OffsetDateTime,
    deletedBy: UUID,
    deleteReason: String?,
  ): Unit =
    suspendTransaction(db = database) {
      val tenantUuid = tenantId.toKotlinUuid()
      val projectUuid = projectId.toKotlinUuid()
      val deletedByUuid = deletedBy.toKotlinUuid()

      IssuesTable.update({
        (IssuesTable.tenantId eq tenantUuid) and
          (IssuesTable.projectId eq projectUuid) and
          IssuesTable.deletedAt.isNull()
      }) {
        it[IssuesTable.deletedAt] = deletedAt
        it[IssuesTable.deletedBy] = deletedByUuid
        it[IssuesTable.deleteReason] = deleteReason
        it[IssuesTable.updatedAt] = deletedAt
      }
      SprintsTable.update({
        (SprintsTable.tenantId eq tenantUuid) and
          (SprintsTable.projectId eq projectUuid) and
          SprintsTable.deletedAt.isNull()
      }) {
        it[SprintsTable.deletedAt] = deletedAt
        it[SprintsTable.deletedBy] = deletedByUuid
        it[SprintsTable.deleteReason] = deleteReason
        it[SprintsTable.updatedAt] = deletedAt
      }
      ProjectIdentifierAliasesTable.update({
        (ProjectIdentifierAliasesTable.tenantId eq tenantUuid) and
          (ProjectIdentifierAliasesTable.projectId eq projectUuid) and
          ProjectIdentifierAliasesTable.validTo.isNull()
      }) {
        it[ProjectIdentifierAliasesTable.validTo] = deletedAt
      }
      IssueHierarchyTable.deleteWhere {
        (IssueHierarchyTable.tenantId eq tenantUuid) and
          (IssueHierarchyTable.projectId eq projectUuid)
      }
    }
}

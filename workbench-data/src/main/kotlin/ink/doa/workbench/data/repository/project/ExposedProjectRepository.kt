package ink.doa.workbench.data.repository.project

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.project.ProjectRepository
import ink.doa.workbench.core.project.model.CreateProjectCommand
import ink.doa.workbench.core.project.model.NonMemberJoinPolicy
import ink.doa.workbench.core.project.model.NonMemberVisibility
import ink.doa.workbench.core.project.model.ProjectRecord
import ink.doa.workbench.core.project.model.ProjectStatus
import ink.doa.workbench.core.project.model.UpdateProjectCommand
import ink.doa.workbench.data.persistence.postgres.project.ProjectIdentifierAliasesTable
import ink.doa.workbench.data.persistence.postgres.project.ProjectsTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedProjectRepository(private val database: Database) : ProjectRepository {
  override suspend fun create(command: CreateProjectCommand): ProjectRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val apiId = PublicId.new("prj")
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      ProjectsTable.insert {
        it[ProjectsTable.id] = id.toKotlinUuid()
        it[ProjectsTable.apiId] = apiId.value
        it[ProjectsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[ProjectsTable.name] = command.name
        it[ProjectsTable.identifier] = command.identifier
        it[ProjectsTable.description] = command.description
        it[ProjectsTable.status] = ProjectStatus.ACTIVE.dbValue
        it[ProjectsTable.nonMemberVisibility] = NonMemberVisibility.INVISIBLE.dbValue
        it[ProjectsTable.nonMemberJoinPolicy] = NonMemberJoinPolicy.ADMIN_ONLY.dbValue
        it[ProjectsTable.leadUserId] = command.leadUserId.toKotlinUuid()
        it[ProjectsTable.createdBy] = command.createdBy.toKotlinUuid()
        it[ProjectsTable.nextIssueSequence] = 1
        it[ProjectsTable.createdAt] = now
        it[ProjectsTable.updatedAt] = now
      }
      ProjectIdentifierAliasesTable.insert {
        it[ProjectIdentifierAliasesTable.id] = UUID.randomUUID().toKotlinUuid()
        it[ProjectIdentifierAliasesTable.tenantId] = command.tenantId.toKotlinUuid()
        it[ProjectIdentifierAliasesTable.projectId] = id.toKotlinUuid()
        it[ProjectIdentifierAliasesTable.identifier] = command.identifier
        it[ProjectIdentifierAliasesTable.isCurrent] = true
        it[ProjectIdentifierAliasesTable.validFrom] = now
        it[ProjectIdentifierAliasesTable.createdBy] = command.createdBy.toKotlinUuid()
        it[ProjectIdentifierAliasesTable.createdAt] = now
      }
      ProjectsTable.selectAll()
        .where { ProjectsTable.id eq id.toKotlinUuid() }
        .single()
        .toProjectRecord()
    }

  override suspend fun findByApiId(tenantId: UUID, apiId: String): ProjectRecord? =
    suspendTransaction(db = database) {
      ProjectsTable.selectAll()
        .where {
          (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and
            (ProjectsTable.apiId eq apiId) and
            ProjectsTable.deletedAt.isNull()
        }
        .singleOrNull()
        ?.toProjectRecord()
    }

  override suspend fun findById(tenantId: UUID, id: UUID): ProjectRecord? =
    suspendTransaction(db = database) {
      ProjectsTable.selectAll()
        .where {
          (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and
            (ProjectsTable.id eq id.toKotlinUuid()) and
            ProjectsTable.deletedAt.isNull()
        }
        .singleOrNull()
        ?.toProjectRecord()
    }

  override suspend fun list(tenantId: UUID, identifier: String?): List<ProjectRecord> =
    suspendTransaction(db = database) {
      var condition =
        (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and
          ProjectsTable.deletedAt.isNull() and
          (ProjectsTable.status neq ProjectStatus.DESTROYING.dbValue)
      if (identifier != null) {
        condition = condition and (ProjectsTable.identifier eq identifier)
      }
      ProjectsTable.selectAll().where { condition }.map { it.toProjectRecord() }
    }

  override suspend fun update(command: UpdateProjectCommand): ProjectRecord =
    suspendTransaction(db = database) {
      val existing =
        ProjectsTable.selectAll()
          .where {
            (ProjectsTable.id eq command.projectId.toKotlinUuid()) and
              (ProjectsTable.tenantId eq command.tenantId.toKotlinUuid()) and
              ProjectsTable.deletedAt.isNull()
          }
          .singleOrNull()
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val previousIdentifier = existing[ProjectsTable.identifier]
      ProjectsTable.update({ ProjectsTable.id eq command.projectId.toKotlinUuid() }) {
        command.identifier?.let { value -> it[ProjectsTable.identifier] = value }
        command.name?.let { value -> it[ProjectsTable.name] = value }
        if (command.description != null) {
          it[ProjectsTable.description] = command.description
        }
        command.nonMemberVisibility?.let { value ->
          it[ProjectsTable.nonMemberVisibility] = value.dbValue
        }
        command.nonMemberJoinPolicy?.let { value ->
          it[ProjectsTable.nonMemberJoinPolicy] = value.dbValue
        }
        it[ProjectsTable.updatedAt] = now
      }
      command.identifier
        ?.takeIf { it != previousIdentifier }
        ?.let { newIdentifier ->
          ProjectIdentifierAliasesTable.update({
            (ProjectIdentifierAliasesTable.projectId eq command.projectId.toKotlinUuid()) and
              (ProjectIdentifierAliasesTable.isCurrent eq true)
          }) {
            it[ProjectIdentifierAliasesTable.isCurrent] = false
            it[ProjectIdentifierAliasesTable.validTo] = now
          }
          ProjectIdentifierAliasesTable.insert {
            it[ProjectIdentifierAliasesTable.id] = UUID.randomUUID().toKotlinUuid()
            it[ProjectIdentifierAliasesTable.tenantId] = command.tenantId.toKotlinUuid()
            it[ProjectIdentifierAliasesTable.projectId] = command.projectId.toKotlinUuid()
            it[ProjectIdentifierAliasesTable.identifier] = newIdentifier
            it[ProjectIdentifierAliasesTable.isCurrent] = true
            it[ProjectIdentifierAliasesTable.validFrom] = now
            it[ProjectIdentifierAliasesTable.createdBy] = command.updatedBy?.toKotlinUuid()
            it[ProjectIdentifierAliasesTable.createdAt] = now
          }
        }
      ProjectsTable.selectAll()
        .where { ProjectsTable.id eq command.projectId.toKotlinUuid() }
        .single()
        .toProjectRecord()
    }

  override suspend fun markArchived(
    tenantId: UUID,
    projectId: UUID,
    archivedAt: OffsetDateTime,
    archivedBy: UUID,
  ): ProjectRecord =
    suspendTransaction(db = database) {
      val updated =
        ProjectsTable.update({
          (ProjectsTable.id eq projectId.toKotlinUuid()) and
            (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and
            ProjectsTable.deletedAt.isNull()
        }) {
          it[ProjectsTable.status] = ProjectStatus.ARCHIVED.dbValue
          it[ProjectsTable.archivedAt] = archivedAt
          it[ProjectsTable.archivedBy] = archivedBy.toKotlinUuid()
          it[ProjectsTable.updatedAt] = archivedAt
        }
      if (updated == 0) {
        throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
      }
      ProjectsTable.selectAll()
        .where { ProjectsTable.id eq projectId.toKotlinUuid() }
        .single()
        .toProjectRecord()
    }

  override suspend fun markActive(tenantId: UUID, projectId: UUID): ProjectRecord =
    suspendTransaction(db = database) {
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val updated =
        ProjectsTable.update({
          (ProjectsTable.id eq projectId.toKotlinUuid()) and
            (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and
            ProjectsTable.deletedAt.isNull()
        }) {
          it[ProjectsTable.status] = ProjectStatus.ACTIVE.dbValue
          it[ProjectsTable.archivedAt] = null
          it[ProjectsTable.archivedBy] = null
          it[ProjectsTable.updatedAt] = now
        }
      if (updated == 0) {
        throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
      }
      ProjectsTable.selectAll()
        .where { ProjectsTable.id eq projectId.toKotlinUuid() }
        .single()
        .toProjectRecord()
    }

  override suspend fun markDestroying(
    tenantId: UUID,
    projectId: UUID,
    deletedBy: UUID,
    deleteReason: String?,
  ): ProjectRecord =
    suspendTransaction(db = database) {
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      val updated =
        ProjectsTable.update({
          (ProjectsTable.id eq projectId.toKotlinUuid()) and
            (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and
            ProjectsTable.deletedAt.isNull()
        }) {
          it[ProjectsTable.status] = ProjectStatus.DESTROYING.dbValue
          it[ProjectsTable.deletedBy] = deletedBy.toKotlinUuid()
          it[ProjectsTable.deleteReason] = deleteReason
          it[ProjectsTable.updatedAt] = now
        }
      if (updated == 0) {
        throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
      }
      ProjectsTable.selectAll()
        .where { ProjectsTable.id eq projectId.toKotlinUuid() }
        .single()
        .toProjectRecord()
    }

  override suspend fun finalizeDestroy(
    tenantId: UUID,
    projectId: UUID,
    deletedAt: OffsetDateTime,
    deletedBy: UUID,
    deleteReason: String?,
  ): Boolean =
    suspendTransaction(db = database) {
      ProjectsTable.update({
        (ProjectsTable.id eq projectId.toKotlinUuid()) and
          (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and
          (ProjectsTable.status eq ProjectStatus.DESTROYING.dbValue) and
          ProjectsTable.deletedAt.isNull()
      }) {
        it[ProjectsTable.deletedAt] = deletedAt
        it[ProjectsTable.deletedBy] = deletedBy.toKotlinUuid()
        it[ProjectsTable.deleteReason] = deleteReason
        it[ProjectsTable.updatedAt] = deletedAt
      } > 0
    }

  override suspend fun updateStatus(
    tenantId: UUID,
    projectId: UUID,
    status: ProjectStatus,
  ): Boolean =
    suspendTransaction(db = database) {
      ProjectsTable.update({
        (ProjectsTable.id eq projectId.toKotlinUuid()) and
          (ProjectsTable.tenantId eq tenantId.toKotlinUuid())
      }) {
        it[ProjectsTable.status] = status.dbValue
        it[ProjectsTable.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
      } > 0
    }

  private fun org.jetbrains.exposed.v1.core.ResultRow.toProjectRecord(): ProjectRecord =
    ProjectRecord(
      id = this[ProjectsTable.id].toJavaUuid(),
      apiId = PublicId(this[ProjectsTable.apiId]),
      tenantId = this[ProjectsTable.tenantId].toJavaUuid(),
      identifier = this[ProjectsTable.identifier],
      name = this[ProjectsTable.name],
      description = this[ProjectsTable.description],
      status = projectStatusOf(this[ProjectsTable.status]),
      nonMemberVisibility = nonMemberVisibilityOf(this[ProjectsTable.nonMemberVisibility]),
      nonMemberJoinPolicy = nonMemberJoinPolicyOf(this[ProjectsTable.nonMemberJoinPolicy]),
      leadUserId = this[ProjectsTable.leadUserId]?.toJavaUuid(),
      createdBy = this[ProjectsTable.createdBy]?.toJavaUuid(),
      archivedAt = this[ProjectsTable.archivedAt],
      archivedBy = this[ProjectsTable.archivedBy]?.toJavaUuid(),
      deletedAt = this[ProjectsTable.deletedAt],
    )
}

internal fun projectStatusOf(value: String): ProjectStatus =
  ProjectStatus.entries.single { it.dbValue == value }

internal fun nonMemberVisibilityOf(value: String): NonMemberVisibility =
  NonMemberVisibility.entries.single { it.dbValue == value }

internal fun nonMemberJoinPolicyOf(value: String): NonMemberJoinPolicy =
  NonMemberJoinPolicy.entries.single { it.dbValue == value }

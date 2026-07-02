package doa.ink.workbench.data.project

import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.ProjectRecord
import doa.ink.workbench.core.project.model.UpdateProjectCommand
import doa.ink.workbench.data.persistence.ProjectsTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
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
        it[ProjectsTable.nextIssueSequence] = 1
        it[ProjectsTable.createdAt] = now
        it[ProjectsTable.updatedAt] = now
      }
      toRecord(
        id,
        apiId,
        command.tenantId,
        command.identifier,
        command.name,
        command.description,
      )
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
        (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and ProjectsTable.deletedAt.isNull()
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
          .singleOrNull() ?: throw ResourceNotFoundException("Project not found.")
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      ProjectsTable.update({ ProjectsTable.id eq command.projectId.toKotlinUuid() }) {
        command.identifier?.let { value -> it[ProjectsTable.identifier] = value }
        command.name?.let { value -> it[ProjectsTable.name] = value }
        command.description?.let { value -> it[ProjectsTable.description] = value }
        it[ProjectsTable.updatedAt] = now
      }
      ProjectsTable.selectAll()
        .where { ProjectsTable.id eq command.projectId.toKotlinUuid() }
        .single()
        .toProjectRecord()
    }

  override suspend fun delete(tenantId: UUID, projectId: UUID): Boolean =
    suspendTransaction(db = database) {
      val now = OffsetDateTime.now(ZoneOffset.UTC)
      ProjectsTable.update({
        (ProjectsTable.id eq projectId.toKotlinUuid()) and
          (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and
          ProjectsTable.deletedAt.isNull()
      }) {
        it[ProjectsTable.deletedAt] = now
        it[ProjectsTable.updatedAt] = now
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
    )

  private fun toRecord(
    id: UUID,
    apiId: PublicId,
    tenantId: UUID,
    identifier: String,
    name: String,
    description: String?,
  ): ProjectRecord =
    ProjectRecord(
      id = id,
      apiId = apiId,
      tenantId = tenantId,
      identifier = identifier,
      name = name,
      description = description,
    )
}

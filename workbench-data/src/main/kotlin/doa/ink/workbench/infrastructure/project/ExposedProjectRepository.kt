package doa.ink.workbench.infrastructure.project

import doa.ink.workbench.infrastructure.persistence.ProjectsTable
import doa.ink.workbench.project.ProjectRepository
import doa.ink.workbench.project.model.CreateProjectCommand
import doa.ink.workbench.project.model.ProjectRecord
import doa.ink.workbench.shared.ids.PublicId
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedProjectRepository(private val database: Database) : ProjectRepository {
  override suspend fun create(command: CreateProjectCommand): ProjectRecord =
    newSuspendedTransaction(db = database) {
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
      ProjectRecord(
        id,
        apiId,
        command.tenantId,
        command.identifier,
        command.name,
        command.description,
      )
    }

  override suspend fun findByApiId(tenantId: UUID, apiId: String): ProjectRecord? =
    newSuspendedTransaction(db = database) {
      ProjectsTable.selectAll()
        .where {
          (ProjectsTable.tenantId eq tenantId.toKotlinUuid()) and (ProjectsTable.apiId eq apiId)
        }
        .singleOrNull()
        ?.let { row ->
          ProjectRecord(
            id = row[ProjectsTable.id].toJavaUuid(),
            apiId = PublicId(row[ProjectsTable.apiId]),
            tenantId = row[ProjectsTable.tenantId].toJavaUuid(),
            identifier = row[ProjectsTable.identifier],
            name = row[ProjectsTable.name],
            description = row[ProjectsTable.description],
          )
        }
    }
}

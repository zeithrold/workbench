package doa.ink.workbench.service.project

import doa.ink.workbench.agile.project.ProjectAccessService
import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.ResourceConflictException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.messaging.EventMetadata
import doa.ink.workbench.core.port.messaging.DomainEventPublisher
import doa.ink.workbench.core.project.ProjectRepository
import doa.ink.workbench.core.project.events.ProjectDestroyRequestedEvent
import doa.ink.workbench.core.project.events.ProjectDomainEvents
import doa.ink.workbench.core.project.model.CreateProjectCommand
import doa.ink.workbench.core.project.model.ProjectRecord
import doa.ink.workbench.core.project.model.ProjectStatus
import doa.ink.workbench.core.project.model.UpdateProjectCommand
import doa.ink.workbench.security.permission.PermissionBootstrapService
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class ProjectManagementService(
  private val projects: ProjectRepository,
  private val users: UserRepository,
  private val projectAccess: ProjectAccessService,
  private val permissionBootstrap: PermissionBootstrapService,
  private val domainEventPublisher: DomainEventPublisher,
  private val clock: Clock,
) {
  suspend fun create(
    command: CreateProjectCommand,
    actorUserId: UUID,
  ): ProjectView {
    val record = projects.create(command)
    permissionBootstrap.provisionProjectCreator(
      tenantId = command.tenantId,
      projectId = record.id,
      userId = actorUserId,
      actorUserId = actorUserId,
    )
    return toView(record)
  }

  suspend fun list(
    tenantId: UUID,
    userId: UUID,
    identifier: String?,
  ): List<ProjectView> =
    projectAccess.listVisibleProjects(userId, tenantId, identifier).map { toView(it) }

  suspend fun get(
    tenantId: UUID,
    userId: UUID,
    projectPublicId: String,
  ): ProjectView {
    val project =
      projects.findByApiId(tenantId, projectPublicId)
        ?: throw ResourceNotFoundException("Project not found.")
    if (!projectAccess.canViewProject(userId, tenantId, project)) {
      throw ResourceNotFoundException("Project not found.")
    }
    return toView(project)
  }

  suspend fun update(command: UpdateProjectCommand): ProjectView {
    val record = projects.update(command)
    return toView(record)
  }

  suspend fun archive(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
  ): ProjectView {
    val archivedAt = OffsetDateTime.now(clock)
    return toView(projects.markArchived(tenantId, projectId, archivedAt, actorUserId))
  }

  suspend fun unarchive(tenantId: UUID, projectId: UUID): ProjectView =
    toView(projects.markActive(tenantId, projectId))

  @Suppress("ThrowsCount")
  suspend fun requestDestroy(
    tenantId: UUID,
    tenantPublicId: doa.ink.workbench.core.common.ids.PublicId,
    projectPublicId: String,
    actorUserId: UUID,
    deleteReason: String?,
  ): ProjectView {
    val project =
      projects.findByApiId(tenantId, projectPublicId)
        ?: throw ResourceNotFoundException("Project not found.")
    if (project.status == ProjectStatus.DESTROYING) {
      throw ResourceConflictException("Project is already being destroyed.")
    }
    val actor =
      users.findById(actorUserId)
        ?: throw InvalidRequestException("Authenticated user is required.")
    val previousStatus = project.status
    val now = OffsetDateTime.now(clock)
    val destroying =
      projects.markDestroying(
        tenantId = tenantId,
        projectId = project.id,
        deletedBy = actorUserId,
        deleteReason = deleteReason,
      )
    try {
      domainEventPublisher.publish(
        spec = ProjectDomainEvents.DestroyRequested,
        key = destroying.apiId.value,
        payload =
          ProjectDestroyRequestedEvent.from(
            project = destroying,
            tenantPublicId = tenantPublicId,
            deleteReason = deleteReason,
            requestedAt = now,
            requestedByPublicId = actor.apiId,
          ),
        metadata = EventMetadata(tenantId = tenantPublicId.value),
      )
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
      projects.updateStatus(tenantId, project.id, previousStatus)
      throw error
    }
    return toView(destroying)
  }

  private suspend fun toView(record: ProjectRecord): ProjectView {
    val lead =
      record.leadUserId?.let { userId ->
        users.findById(userId)?.let(UserSummary::from)
          ?: throw ResourceNotFoundException("User not found.")
      }
    return ProjectView.from(record, lead)
  }
}

data class ProjectView(
  val id: String,
  val identifier: String,
  val name: String,
  val description: String?,
  val status: String,
  val nonMemberVisibility: String,
  val nonMemberJoinPolicy: String,
  val lead: UserSummary?,
  val archivedAt: String?,
) {
  companion object {
    fun from(record: ProjectRecord, lead: UserSummary?) =
      ProjectView(
        id = record.apiId.value,
        identifier = record.identifier,
        name = record.name,
        description = record.description,
        status = record.status.dbValue,
        nonMemberVisibility = record.nonMemberVisibility.dbValue,
        nonMemberJoinPolicy = record.nonMemberJoinPolicy.dbValue,
        lead = lead,
        archivedAt = record.archivedAt?.toString(),
      )
  }
}

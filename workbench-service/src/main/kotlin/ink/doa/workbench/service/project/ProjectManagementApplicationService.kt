package ink.doa.workbench.service.project

import ink.doa.workbench.core.common.errors.ResourceConflictException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.summary.ProjectSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.common.warning.WorkbenchWarningCode
import ink.doa.workbench.core.common.warning.meta.ProjectDestroyScheduledMeta
import ink.doa.workbench.core.project.ProjectDestroyRequest
import ink.doa.workbench.core.project.events.ProjectDestroyRequestedEvent
import ink.doa.workbench.core.project.model.CreateProjectCommand
import ink.doa.workbench.core.project.model.ProjectRecord
import ink.doa.workbench.core.project.model.ProjectStatus
import ink.doa.workbench.core.project.model.UpdateProjectCommand
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class ProjectManagementApplicationService(private val dependencies: ProjectManagementDependencies) {
  private val projects = dependencies.projects
  private val userLookupService = dependencies.userLookupService
  private val projectAccess = dependencies.projectAccess
  private val permissionBootstrap = dependencies.permissionBootstrap
  private val warningCollector = dependencies.infrastructure.warningCollector
  private val clock = dependencies.infrastructure.clock

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
    val project = projects.get(tenantId, projectPublicId)
    if (!projectAccess.canViewProject(userId, tenantId, project)) {
      throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_PROJECT_NOT_FOUND)
    }
    return toView(project)
  }

  suspend fun update(command: UpdateProjectCommand): ProjectView = toView(projects.update(command))

  suspend fun archive(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
  ): ProjectView {
    val archivedAt = OffsetDateTime.now(clock)
    return toView(projects.archive(tenantId, projectId, archivedAt, actorUserId))
  }

  suspend fun unarchive(tenantId: UUID, projectId: UUID): ProjectView =
    toView(projects.unarchive(tenantId, projectId))

  @Suppress("ThrowsCount")
  suspend fun requestDestroy(
    tenantId: UUID,
    tenantPublicId: PublicId,
    projectPublicId: String,
    actorUserId: UUID,
    deleteReason: String?,
  ): ProjectView {
    val project = projects.get(tenantId, projectPublicId)
    if (project.status == ProjectStatus.DESTROYING) {
      throw ResourceConflictException(WorkbenchErrorCode.PROJECT_ALREADY_DESTROYING)
    }
    val actor = userLookupService.requireAuthenticatedUser(actorUserId)
    val now = OffsetDateTime.now(clock)
    val destroying =
      projects.requestDestroy(
        ProjectDestroyRequest(
          tenantId = tenantId,
          projectId = project.id,
          deletedBy = actorUserId,
          deleteReason = deleteReason,
          projectApiId = project.apiId.value,
          tenantApiId = tenantPublicId.value,
          payload =
            ProjectDestroyRequestedEvent.from(
              project = project,
              tenantPublicId = tenantPublicId,
              deleteReason = deleteReason,
              requestedAt = now,
              requestedByPublicId = actor.apiId,
            ),
        )
      )
    warningCollector.warn(
      WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
      meta =
        ProjectDestroyScheduledMeta(
          project = ProjectSummary.from(destroying),
          deleteReason = deleteReason,
        ),
    )
    return toView(destroying)
  }

  private suspend fun toView(record: ProjectRecord): ProjectView {
    val lead =
      record.leadUserId?.let { userId ->
        UserSummary.from(userLookupService.requireUser(userId))
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

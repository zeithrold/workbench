package ink.doa.workbench.agile.project

import ink.doa.workbench.agile.project.model.NonMemberVisibility
import ink.doa.workbench.agile.project.model.ProjectRecord
import ink.doa.workbench.agile.project.model.ProjectStatus
import ink.doa.workbench.identity.permission.PermissionBindingRepository
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class ProjectAccessService(
  private val projects: ProjectRepository,
  private val bindings: PermissionBindingRepository,
  private val clock: Clock,
) {
  suspend fun hasTenantWideProjectAccess(userId: UUID, tenantId: UUID): Boolean {
    val at = now()
    val rules = bindings.listActiveRulesForSubject(userId, tenantId, projectId = null, at = at)
    return rules.any {
      it.action == AuthorizationAction("project.read") &&
        (it.resourcePattern == "project:*" || it.resourcePattern == "*")
    }
  }

  suspend fun isProjectMember(userId: UUID, tenantId: UUID, projectId: UUID): Boolean =
    bindings.listProjectIdsForSubject(tenantId, userId, now()).contains(projectId)

  suspend fun canViewProject(
    userId: UUID,
    tenantId: UUID,
    project: ProjectRecord,
  ): Boolean {
    if (project.status == ProjectStatus.DESTROYING) return false
    if (hasTenantWideProjectAccess(userId, tenantId)) return true
    if (isProjectMember(userId, tenantId, project.id)) return true
    return project.nonMemberVisibility != NonMemberVisibility.INVISIBLE
  }

  suspend fun allowsVisibilityAction(
    userId: UUID,
    tenantId: UUID,
    projectId: UUID,
    action: AuthorizationAction,
  ): Boolean {
    if (hasTenantWideProjectAccess(userId, tenantId)) return false
    if (isProjectMember(userId, tenantId, projectId)) return false
    val project = projects.findById(tenantId, projectId) ?: return false
    return when (project.nonMemberVisibility) {
      NonMemberVisibility.INVISIBLE -> false
      NonMemberVisibility.READ_ONLY -> action in READ_ONLY_VISIBILITY_ACTIONS
      NonMemberVisibility.READ_WRITE -> action in READ_WRITE_VISIBILITY_ACTIONS
    }
  }

  private companion object {
    private val READ_ONLY_VISIBILITY_ACTIONS =
      setOf(
        AuthorizationAction("project.read"),
        AuthorizationAction("issue.view"),
      )
    private val READ_WRITE_VISIBILITY_ACTIONS =
      setOf(
        AuthorizationAction("project.read"),
        AuthorizationAction("project.update"),
        AuthorizationAction("issue.view"),
        AuthorizationAction("issue.create"),
        AuthorizationAction("issue.update"),
        AuthorizationAction("issue.transition"),
        AuthorizationAction("issue.field.write"),
      )
  }

  suspend fun listVisibleProjects(
    userId: UUID,
    tenantId: UUID,
    identifier: String?,
  ): List<ProjectRecord> {
    val allProjects = projects.list(tenantId, identifier)
    if (hasTenantWideProjectAccess(userId, tenantId)) return allProjects
    val memberProjectIds = bindings.listProjectIdsForSubject(tenantId, userId, now())
    return allProjects.filter { project ->
      memberProjectIds.contains(project.id) ||
        project.nonMemberVisibility != NonMemberVisibility.INVISIBLE
    }
  }

  private fun now(): OffsetDateTime = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
}

package one.ztd.workbench.agile.workitem

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.agile.project.ProjectAccessService
import one.ztd.workbench.agile.workitem.view.WorkItemViewRecord
import one.ztd.workbench.agile.workitem.view.WorkItemViewVisibility
import one.ztd.workbench.identity.TenantMemberRepository
import one.ztd.workbench.identity.permission.PermissionBindingRepository
import one.ztd.workbench.identity.permission.ResolvedPermissionRule
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.kernel.common.errors.PermissionDeniedException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Service

@Service
class WorkItemViewAccessService(
  private val bindings: PermissionBindingRepository,
  private val projectAccess: ProjectAccessService,
  private val tenantMembers: TenantMemberRepository,
  private val clock: Clock,
) {
  suspend fun requireCreate(tenantId: UUID, projectId: UUID?, actorUserId: UUID) {
    if (!canCreate(tenantId, projectId, actorUserId)) {
      throw PermissionDeniedException(WorkbenchErrorCode.WORK_ITEM_VIEW_CREATE_DENIED)
    }
  }

  suspend fun requireRead(view: WorkItemViewRecord, actorUserId: UUID) {
    if (!canRead(view, actorUserId)) {
      throw PermissionDeniedException(WorkbenchErrorCode.WORK_ITEM_VIEW_READ_DENIED)
    }
  }

  suspend fun requireManage(view: WorkItemViewRecord, actorUserId: UUID) {
    if (!canManage(view, actorUserId)) {
      throw PermissionDeniedException(WorkbenchErrorCode.WORK_ITEM_VIEW_MANAGE_DENIED)
    }
  }

  suspend fun canRead(view: WorkItemViewRecord, actorUserId: UUID): Boolean {
    if (view.ownerId == actorUserId) return true
    return WorkItemViewAccessRules.canRead(
      view = view,
      actorUserId = actorUserId,
      rules = activeRules(view.tenantId, view.projectId, actorUserId),
      projectAccess = projectAccess,
      tenantMembers = tenantMembers,
    )
  }

  suspend fun canManage(view: WorkItemViewRecord, actorUserId: UUID): Boolean {
    if (view.ownerId == actorUserId) return true
    return WorkItemViewAccessRules.canManage(
      view = view,
      actorUserId = actorUserId,
      rules = activeRules(view.tenantId, view.projectId, actorUserId),
    )
  }

  private suspend fun canCreate(tenantId: UUID, projectId: UUID?, actorUserId: UUID): Boolean =
    WorkItemViewAccessRules.canCreate(rules = activeRules(tenantId, projectId, actorUserId))

  private suspend fun activeRules(
    tenantId: UUID,
    projectId: UUID?,
    actorUserId: UUID,
  ): List<ResolvedPermissionRule> =
    bindings.listActiveRulesForSubject(
      subjectUserId = actorUserId,
      tenantId = tenantId,
      projectId = projectId,
      at = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
    )
}

private object WorkItemViewAccessRules {
  suspend fun canRead(
    view: WorkItemViewRecord,
    actorUserId: UUID,
    rules: List<ResolvedPermissionRule>,
    projectAccess: ProjectAccessService,
    tenantMembers: TenantMemberRepository,
  ): Boolean {
    if (view.ownerId == actorUserId) return true
    if (hasViewAction(rules, READ_ACTION, view.apiId.value)) return true
    return when (view.visibility) {
      WorkItemViewVisibility.PRIVATE -> false
      WorkItemViewVisibility.PROJECT -> {
        val scopedProjectId = view.projectId
        scopedProjectId != null &&
          isProjectReader(projectAccess, rules, view.tenantId, scopedProjectId, actorUserId)
      }
      WorkItemViewVisibility.TENANT ->
        tenantMembers.findByTenantAndUser(view.tenantId, actorUserId) != null
    }
  }

  fun canManage(
    view: WorkItemViewRecord,
    actorUserId: UUID,
    rules: List<ResolvedPermissionRule>,
  ): Boolean = view.ownerId == actorUserId || hasViewAction(rules, MANAGE_ACTION, view.apiId.value)

  fun canCreate(rules: List<ResolvedPermissionRule>): Boolean =
    hasViewAction(rules, CREATE_ACTION, viewApiId = null)

  private suspend fun isProjectReader(
    projectAccess: ProjectAccessService,
    rules: List<ResolvedPermissionRule>,
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
  ): Boolean {
    if (projectAccess.isProjectMember(actorUserId, tenantId, projectId)) {
      return rules.any {
        it.effect == PermissionEffect.ALLOW &&
          it.action == ISSUE_VIEW_ACTION &&
          resourceMatches(it.resourcePattern, "issue:*")
      }
    }
    return projectAccess.allowsVisibilityAction(
      actorUserId,
      tenantId,
      projectId,
      ISSUE_VIEW_ACTION,
    )
  }

  private fun hasViewAction(
    rules: List<ResolvedPermissionRule>,
    action: AuthorizationAction,
    viewApiId: String?,
  ): Boolean = rules.any {
    it.effect == PermissionEffect.ALLOW &&
      it.action == action &&
      resourceMatches(it.resourcePattern, viewResource(viewApiId))
  }

  private fun viewResource(viewApiId: String?): String =
    if (viewApiId == null) "view:*" else "view:$viewApiId"

  private fun resourceMatches(pattern: String, resource: String): Boolean =
    pattern == "*" ||
      pattern == resource ||
      (pattern.endsWith(":*") && resource.startsWith(pattern.removeSuffix("*")))

  private val READ_ACTION = AuthorizationAction("view.read")
  private val CREATE_ACTION = AuthorizationAction("view.create")
  private val MANAGE_ACTION = AuthorizationAction("view.manage")
  private val ISSUE_VIEW_ACTION = AuthorizationAction("issue.view")
}

package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.ResolvedPermissionRule
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.PermissionEffect
import ink.doa.workbench.core.workitem.template.TemplateField
import ink.doa.workbench.core.workitem.template.toPermissionResourceId
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.stereotype.Service

enum class FieldPermissionOperation {
  CREATE,
  UPDATE,
}

data class WorkItemFieldPermissionContext(
  val tenantId: UUID,
  val projectId: UUID,
  val actorUserId: UUID,
  val operation: FieldPermissionOperation,
)

@Service
class WorkItemFieldPermissionService(
  private val bindings: PermissionBindingRepository,
  private val clock: Clock,
) {
  @Suppress("ReturnCount")
  suspend fun canWriteField(
    context: WorkItemFieldPermissionContext,
    field: TemplateField,
  ): Boolean {
    val rules =
      bindings.listActiveRulesForSubject(
        subjectUserId = context.actorUserId,
        tenantId = context.tenantId,
        projectId = context.projectId,
        at = now(),
      )
    val fieldResource = field.toPermissionResourceId()
    val fieldRules = rules.filter { it.matchesFieldWrite(fieldResource) }
    if (fieldRules.any { it.effect == PermissionEffect.DENY }) return false
    if (fieldRules.any { it.effect == PermissionEffect.ALLOW }) return true
    return when (context.operation) {
      FieldPermissionOperation.CREATE -> true
      FieldPermissionOperation.UPDATE -> rules.any { it.matchesIssueUpdate() }
    }
  }

  suspend fun isFieldEditableInTransition(
    context: WorkItemFieldPermissionContext,
    field: TemplateField,
    writeGrant: ink.doa.workbench.core.workitem.template.FieldWriteGrant,
  ): Boolean =
    when (writeGrant) {
      ink.doa.workbench.core.workitem.template.FieldWriteGrant.IMMUTABLE,
      ink.doa.workbench.core.workitem.template.FieldWriteGrant.SYSTEM_ONLY -> false
      ink.doa.workbench.core.workitem.template.FieldWriteGrant.TRANSITION_WRITABLE -> true
      ink.doa.workbench.core.workitem.template.FieldWriteGrant.INHERIT ->
        canWriteField(context.copy(operation = FieldPermissionOperation.UPDATE), field)
    }

  private fun ResolvedPermissionRule.matchesFieldWrite(fieldResource: String): Boolean =
    action == FIELD_WRITE_ACTION &&
      (resourcePattern == "issue:field:*" || resourcePattern == fieldResource)

  private fun ResolvedPermissionRule.matchesIssueUpdate(): Boolean =
    action == ISSUE_UPDATE_ACTION && (resourcePattern == "issue:*" || resourcePattern == "*")

  private fun now(): OffsetDateTime = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

  private companion object {
    val FIELD_WRITE_ACTION = AuthorizationAction("issue.field.write")
    val ISSUE_UPDATE_ACTION = AuthorizationAction("issue.update")
  }
}

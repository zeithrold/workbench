package one.ztd.workbench.agile.workitem

import java.util.UUID
import one.ztd.workbench.agile.workitem.access.AccessConditionContext
import one.ztd.workbench.agile.workitem.access.WorkItemAccessActor
import one.ztd.workbench.agile.workitem.access.WorkItemAccessRuleRecord
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigDetails
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigPropertyRecord
import one.ztd.workbench.agile.workitem.model.WorkItemFieldCapability
import one.ztd.workbench.agile.workitem.model.WorkItemFieldCapabilityState
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.agile.workitem.model.WorkItemSearchHit
import one.ztd.workbench.agile.workitem.template.TemplateField
import one.ztd.workbench.identity.permission.ResolvedPermissionRule
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

@Component
class WorkItemCapabilityContextLoader(
  private val repository: WorkItemRepository,
  private val configs: IssueTypeConfigRepository,
  private val contextLoader: WorkItemTransitionContextLoader,
  private val bindingPermissions: WorkItemBindingPermissionEvaluator,
  private val accessPolicy: WorkItemAccessPolicyEngine,
) {
  suspend fun load(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
    actorUserApiId: String,
    hits: List<WorkItemSearchHit>,
  ): WorkItemCapabilityBatch {
    val bindingRules = bindingPermissions.loadActiveRules(tenantId, projectId, actorUserId)
    val projectConfigs = configs.listConfigs(tenantId, projectId).filter { it.config.isActive }
    return WorkItemCapabilityBatch(
      actor =
        WorkItemCapabilityActor(
          tenantId = tenantId,
          userId = actorUserId,
          userApiId = actorUserApiId,
          accessActor = accessPolicy.resolveActor(tenantId, projectId, actorUserId),
          bindingRules = bindingRules,
        ),
      issuesById =
        repository
          .findByDatabaseIds(tenantId, projectId, hits.map { it.databaseId }.toSet())
          .associateBy { it.id },
      configsByApiId = projectConfigs.associateBy { it.config.apiId.value },
      configuredProperties = projectConfigs.flatMap { it.properties }.distinctBy { it.code },
    )
  }

  suspend fun context(
    batch: WorkItemCapabilityBatch,
    hit: WorkItemSearchHit,
  ): WorkItemTransitionContext? {
    val issue = batch.issuesById[hit.databaseId] ?: return null
    val config = batch.configsByApiId[issue.issueTypeConfigApiId.value] ?: return null
    val accessRules =
      batch.accessRulesByConfig.getOrPut(config.config.id) {
        accessPolicy.loadAccessRules(batch.actor.tenantId, config.config.id)
      }
    return contextLoader.load(
      issue,
      batch.actor.userId,
      batch.actor.userApiId,
      WorkItemTransitionContextLoader.Overrides(
        bindingRules = batch.actor.bindingRules,
        accessRules = accessRules,
        config = config,
        actor = batch.actor.accessActor,
        projectApiId = hit.projectApiId,
      ),
    )
  }
}

data class WorkItemCapabilityBatch(
  val actor: WorkItemCapabilityActor,
  val issuesById: Map<UUID, WorkItemRecord>,
  val configsByApiId: Map<String, IssueTypeConfigDetails>,
  val configuredProperties: List<IssueTypeConfigPropertyRecord>,
  val accessRulesByConfig: MutableMap<UUID, List<WorkItemAccessRuleRecord>> = mutableMapOf(),
)

data class WorkItemCapabilityActor(
  val tenantId: UUID,
  val userId: UUID,
  val userApiId: String,
  val accessActor: WorkItemAccessActor,
  val bindingRules: List<ResolvedPermissionRule>,
)

@Service
class WorkItemFieldCapabilityService(
  private val contexts: WorkItemCapabilityContextLoader,
  private val transitions: WorkItemTransitionService,
  private val permissions: WorkItemFieldPermissionService,
  private val bindingPermissions: WorkItemBindingPermissionEvaluator,
) {
  suspend fun attach(
    tenantId: UUID,
    projectId: UUID,
    actorUserId: UUID,
    actorUserApiId: String,
    hits: List<WorkItemSearchHit>,
  ): List<WorkItemSearchHit> {
    if (hits.isEmpty()) return hits
    val batch = contexts.load(tenantId, projectId, actorUserId, actorUserApiId, hits)
    return hits.map { hit ->
      val context = contexts.context(batch, hit) ?: return@map hit
      hit.copy(fieldCapabilities = capabilities(batch, context))
    }
  }

  private suspend fun capabilities(
    batch: WorkItemCapabilityBatch,
    context: WorkItemTransitionContext,
  ): Map<String, WorkItemFieldCapability> =
    linkedMapOf<String, WorkItemFieldCapability>().apply {
      listOf("assignee", "priority", "sprint").forEach { name ->
        this[name] = capability(context, TemplateField.System(name))
      }
      val applicableProperties = context.config.properties.associateBy { it.code }
      batch.configuredProperties.forEach { property ->
        this["property.${property.code}"] =
          propertyCapability(context, property, applicableProperties)
      }
      this["status"] = statusCapability(batch, context)
    }

  private suspend fun propertyCapability(
    context: WorkItemTransitionContext,
    property: IssueTypeConfigPropertyRecord,
    applicableProperties: Map<String, IssueTypeConfigPropertyRecord>,
  ): WorkItemFieldCapability =
    if (property.code !in applicableProperties) {
      WorkItemFieldCapability(WorkItemFieldCapabilityState.UNAVAILABLE, REASON_NOT_APPLICABLE)
    } else {
      capability(context, TemplateField.Property(apiId = null, code = property.code))
    }

  private suspend fun statusCapability(
    batch: WorkItemCapabilityBatch,
    context: WorkItemTransitionContext,
  ): WorkItemFieldCapability {
    val conditionContext =
      AccessConditionContext.fromResourceAttributes(
        actorUserApiId = batch.actor.userApiId,
        resourceAttributes = context.permissionContext.resourceAttributes,
      )
    val transitionGranted =
      bindingPermissions.allowsIssueAction(
        batch.actor.bindingRules,
        ISSUE_TRANSITION_ACTION,
        conditionContext,
      )
    val canTransition =
      transitionGranted && transitions.availableTransitions(context).any { it.enabled }
    return if (canTransition) {
      WorkItemFieldCapability(WorkItemFieldCapabilityState.EDITABLE)
    } else {
      WorkItemFieldCapability(
        WorkItemFieldCapabilityState.READ_ONLY,
        if (transitionGranted) REASON_NO_TRANSITION else REASON_PERMISSION_DENIED,
      )
    }
  }

  private suspend fun capability(
    context: WorkItemTransitionContext,
    field: TemplateField,
  ): WorkItemFieldCapability =
    if (permissions.resolvePatchPolicy(context.permissionContext, field).allowsPatchSubmission()) {
      WorkItemFieldCapability(WorkItemFieldCapabilityState.EDITABLE)
    } else {
      WorkItemFieldCapability(WorkItemFieldCapabilityState.READ_ONLY, REASON_PERMISSION_DENIED)
    }

  private companion object {
    val ISSUE_TRANSITION_ACTION = AuthorizationAction("issue.transition")
    const val REASON_PERMISSION_DENIED = "permission_denied"
    const val REASON_NOT_APPLICABLE = "field_not_applicable"
    const val REASON_NO_TRANSITION = "no_available_transition"
  }
}

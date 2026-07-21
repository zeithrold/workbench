package one.ztd.workbench.agile.workitem

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.agile.testfixtures.AgileWorkItemFixtures
import one.ztd.workbench.agile.workitem.access.WorkItemAccessActor
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigPropertyRecord
import one.ztd.workbench.agile.workitem.model.WorkItemFieldCapability
import one.ztd.workbench.agile.workitem.model.WorkItemFieldCapabilityState
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyDataType
import one.ztd.workbench.agile.workitem.model.WorkItemTransitionOption
import one.ztd.workbench.kernel.common.ids.PublicId

class WorkItemCapabilityContextLoaderTest :
  StringSpec({
    "loads shared permission context once and reuses access rules per config" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val actorId = UUID.randomUUID()
      val config = AgileWorkItemFixtures.sampleConfig(tenantId)
      val property = capabilityProperty(tenantId, config.config.id, "points")
      val activeConfig = config.copy(properties = listOf(property))
      val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, activeConfig, actorId)
      val hit = AgileWorkItemFixtures.searchHit(issue)
      val repository = mockk<WorkItemRepository>()
      val configs = mockk<IssueTypeConfigRepository>()
      val transitionContexts = mockk<WorkItemTransitionContextLoader>()
      val bindingPermissions = mockk<WorkItemBindingPermissionEvaluator>()
      val accessPolicy = mockk<WorkItemAccessPolicyEngine>()
      val actor = WorkItemAccessActor(actorId, "usr_actor", emptySet(), emptySet())
      val transitionContext = mockk<WorkItemTransitionContext>()
      coEvery { bindingPermissions.loadActiveRules(tenantId, projectId, actorId) } returns
        emptyList()
      coEvery { configs.listConfigs(tenantId, projectId) } returns listOf(activeConfig)
      coEvery { repository.findByDatabaseIds(tenantId, projectId, setOf(issue.id)) } returns
        listOf(issue)
      coEvery { accessPolicy.resolveActor(tenantId, projectId, actorId) } returns actor
      coEvery { accessPolicy.loadAccessRules(tenantId, config.config.id) } returns emptyList()
      coEvery { transitionContexts.load(issue, actorId, "usr_actor", any()) } returns
        transitionContext
      val loader =
        WorkItemCapabilityContextLoader(
          repository,
          configs,
          transitionContexts,
          bindingPermissions,
          accessPolicy,
        )

      val batch = loader.load(tenantId, projectId, actorId, "usr_actor", listOf(hit))

      batch.configuredProperties shouldBe listOf(property)
      loader.context(batch, hit) shouldBe transitionContext
      loader.context(batch, hit) shouldBe transitionContext
      loader.context(batch, hit.copy(databaseId = UUID.randomUUID())) shouldBe null
      coVerify(exactly = 1) { accessPolicy.loadAccessRules(tenantId, config.config.id) }
    }
  })

class WorkItemFieldCapabilityServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val config = AgileWorkItemFixtures.sampleConfig(tenantId)
    val applicable = capabilityProperty(tenantId, config.config.id, "points")
    val unavailable = capabilityProperty(tenantId, config.config.id, "team")
    val activeConfig = config.copy(properties = listOf(applicable))
    val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, activeConfig, actorId)
    val hit = AgileWorkItemFixtures.searchHit(issue)
    val permissionContext = mockk<WorkItemFieldPermissionContext>()
    val transitionContext = mockk<WorkItemTransitionContext>()
    val contexts = mockk<WorkItemCapabilityContextLoader>()
    val transitions = mockk<WorkItemTransitionService>()
    val permissions = mockk<WorkItemFieldPermissionService>()
    val bindingPermissions = mockk<WorkItemBindingPermissionEvaluator>()
    val actor = WorkItemAccessActor(actorId, "usr_actor", emptySet(), emptySet())
    val batch =
      WorkItemCapabilityBatch(
        actor = WorkItemCapabilityActor(tenantId, actorId, "usr_actor", actor, emptyList()),
        issuesById = mapOf(issue.id to issue),
        configsByApiId = mapOf(config.config.apiId.value to activeConfig),
        configuredProperties = listOf(applicable, unavailable),
      )
    val service =
      WorkItemFieldCapabilityService(contexts, transitions, permissions, bindingPermissions)

    every { transitionContext.config } returns activeConfig
    every { transitionContext.permissionContext } returns permissionContext
    every { permissionContext.resourceAttributes } returns mapOf("status" to "todo")
    coEvery { contexts.load(tenantId, projectId, actorId, "usr_actor", listOf(hit)) } returns batch
    coEvery { contexts.context(batch, hit) } returns transitionContext

    "marks writable and applicable fields editable and requires an enabled transition" {
      coEvery { permissions.resolvePatchPolicy(permissionContext, any()) } returns
        FieldMutationPolicy(FieldSubmissionPolicy.INHERIT_BINDING, bindingAllowsWrite = true)
      every {
        bindingPermissions.allowsIssueAction(
          any(),
          one.ztd.workbench.identity.permission.model.AuthorizationAction("issue.transition"),
          any(),
        )
      } returns true
      coEvery { transitions.availableTransitions(transitionContext) } returns
        listOf(
          mockk<WorkItemTransitionOption> {
            every { enabled } returns true
          }
        )

      val capabilities =
        service
          .attach(tenantId, projectId, actorId, "usr_actor", listOf(hit))
          .single()
          .fieldCapabilities

      capabilities shouldContain
        ("status" to WorkItemFieldCapability(WorkItemFieldCapabilityState.EDITABLE))
      capabilities shouldContain
        ("property.points" to WorkItemFieldCapability(WorkItemFieldCapabilityState.EDITABLE))
      capabilities shouldContain
        ("property.team" to
          WorkItemFieldCapability(
            WorkItemFieldCapabilityState.UNAVAILABLE,
            "field_not_applicable",
          ))
    }

    "keeps denied fields read only and leaves hits without a context unchanged" {
      coEvery { permissions.resolvePatchPolicy(permissionContext, any()) } returns
        FieldMutationPolicy(FieldSubmissionPolicy.READ_ONLY, bindingAllowsWrite = false)
      every {
        bindingPermissions.allowsIssueAction(
          any(),
          one.ztd.workbench.identity.permission.model.AuthorizationAction("issue.transition"),
          any(),
        )
      } returns false
      coEvery { contexts.context(batch, hit) } returns transitionContext andThen null

      val denied = service.attach(tenantId, projectId, actorId, "usr_actor", listOf(hit)).single()
      val unchanged =
        service.attach(tenantId, projectId, actorId, "usr_actor", listOf(hit)).single()

      denied.fieldCapabilities["assignee"]?.state shouldBe WorkItemFieldCapabilityState.READ_ONLY
      denied.fieldCapabilities["status"]?.reason shouldBe "permission_denied"
      unchanged shouldBe hit
      service.attach(tenantId, projectId, actorId, "usr_actor", emptyList()) shouldBe emptyList()
    }

    "reports no available transition when transition permission is granted" {
      coEvery { permissions.resolvePatchPolicy(permissionContext, any()) } returns
        FieldMutationPolicy(FieldSubmissionPolicy.INHERIT_BINDING, bindingAllowsWrite = true)
      every {
        bindingPermissions.allowsIssueAction(
          any(),
          one.ztd.workbench.identity.permission.model.AuthorizationAction("issue.transition"),
          any(),
        )
      } returns true
      coEvery { transitions.availableTransitions(transitionContext) } returns emptyList()
      coEvery { contexts.context(batch, hit) } returns transitionContext

      val result = service.attach(tenantId, projectId, actorId, "usr_actor", listOf(hit)).single()

      result.fieldCapabilities["status"]?.reason shouldBe "no_available_transition"
    }
  })

private fun capabilityProperty(
  tenantId: UUID,
  configId: UUID,
  code: String,
) =
  IssueTypeConfigPropertyRecord(
    id = UUID.randomUUID(),
    tenantId = tenantId,
    issueTypeConfigId = configId,
    propertyId = UUID.randomUUID(),
    propertyApiId = PublicId.new("fld"),
    code = code,
    name = code.replaceFirstChar(Char::uppercase),
    dataType = WorkItemPropertyDataType.NUMBER,
    validationOverride = JsonObject(emptyMap()),
    rank = 100,
    displayConfig = JsonObject(emptyMap()),
  )

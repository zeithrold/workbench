package ink.doa.workbench.agile.workitem

import ink.doa.workbench.agile.testfixtures.AgileServiceFactory
import ink.doa.workbench.agile.testfixtures.AgileWorkItemFixtures
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.workitem.template.TransitionFieldsParser
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class WorkItemTransitionOptionBuilderTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-04T10:15:30Z"), ZoneOffset.UTC)
    val fieldPermissions = mockk<WorkItemFieldPermissionService>()
    val fieldPipeline =
      WorkItemFieldMutationPipeline(
        engine = WorkItemFieldMutationEngine(fieldPermissions, clock),
        transitionFieldsParser = TransitionFieldsParser(),
      )
    val accessPolicy = AgileServiceFactory.mockAccessPolicy()
    val evaluator =
      WorkItemTransitionEvaluator(
        WorkItemTransitionValidator(mockk(relaxed = true), accessPolicy),
        accessPolicy,
        TransitionFieldsParser(),
      )
    val builder = WorkItemTransitionOptionBuilder(fieldPipeline)

    "build marks transition enabled when preconditions pass" {
      val buildContext = sampleBuildContext()
      val transition = AgileWorkItemFixtures.sampleTransition(buildContext.config)
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns fieldMutationPolicy()

      val option = runBlocking {
        builder.build(
          transition,
          buildContext,
          evaluator.evaluate(buildContext, transition),
        )
      }

      option.id shouldBe transition.apiId
      option.toStatusId shouldBe buildContext.config.statuses.single().statusApiId
      option.enabled shouldBe true
    }

    "build disables transition when target status is unavailable" {
      val buildContext = sampleBuildContext()
      val missingStatusId = UUID.randomUUID()
      val transition =
        AgileWorkItemFixtures.sampleTransition(buildContext.config)
          .copy(
            toStatusId = missingStatusId,
            toStatusApiId = PublicId.new("sts"),
          )
      coEvery { fieldPermissions.resolvePolicy(any(), any(), any()) } returns
        readOnlyFieldMutationPolicy()

      val option = runBlocking {
        builder.build(
          transition,
          buildContext,
          evaluator.evaluate(buildContext, transition),
        )
      }

      option.enabled shouldBe false
      option.reason shouldBe "Transition target status is not available in this type config."
    }
  })

private const val ACTOR_API_ID = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0"
private const val PROJECT_API_ID = "prj_01JABCDEFGHJKMNPQRSTVWXYZ1"

private fun sampleBuildContext(): WorkItemTransitionContext {
  val tenantId = UUID.randomUUID()
  val projectId = UUID.randomUUID()
  val actorUserId = UUID.randomUUID()
  val config = AgileWorkItemFixtures.sampleConfig(tenantId)
  val issue = AgileWorkItemFixtures.sampleIssue(tenantId, projectId, config, actorUserId)
  val accessEvaluation =
    ink.doa.workbench.core.workitem.access.WorkItemAccessEvaluationContext(
      actor =
        ink.doa.workbench.core.workitem.access.WorkItemAccessActor(
          userId = actorUserId,
          userApiId = ACTOR_API_ID,
          groupIds = emptySet(),
          projectRoles = emptySet(),
        ),
      workItem = issue,
      issueTypeConfigId = config.config.id,
      projectApiId = PROJECT_API_ID,
      properties = emptyMap(),
    )
  return WorkItemTransitionContext(
    tenantId = tenantId,
    projectId = projectId,
    actorUserId = actorUserId,
    actorUserApiId = ACTOR_API_ID,
    issue = issue,
    config = config,
    currentProperties = emptyMap(),
    conditionContext = WorkItemConditionContext(issue, ACTOR_API_ID, PROJECT_API_ID, emptyMap()),
    accessEvaluation = accessEvaluation,
    templateContext =
      ink.doa.workbench.core.workitem.template.WorkItemValueTemplateContext(
        tenantId = tenantId,
        projectId = projectId,
        currentUserApiId = ACTOR_API_ID,
        currentProjectApiId = PROJECT_API_ID,
        actorUserId = actorUserId,
      ),
    permissionContext =
      WorkItemFieldPermissionContext(
        tenantId = tenantId,
        projectId = projectId,
        actorUserId = actorUserId,
        actorUserApiId = ACTOR_API_ID,
        operation = FieldPermissionOperation.UPDATE,
      ),
  )
}

package one.ztd.workbench.agile.workitem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.testfixtures.AgileWorkItemFixtures
import one.ztd.workbench.agile.workitem.model.IssueTypeConfigPropertyRecord
import one.ztd.workbench.agile.workitem.model.PropertyDefinitionRecord
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyDataType
import one.ztd.workbench.agile.workitem.model.WorkItemRecord
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.ids.PublicId

class WorkItemDisplayFieldCatalogServiceTest :
  StringSpec({
    "returns system fields plus the active configured property union" {
      val tenantId = UUID.randomUUID()
      val projectId = UUID.randomUUID()
      val propertyId = UUID.randomUUID()
      val configs = mockk<IssueTypeConfigRepository>()
      val properties = mockk<PropertyDefinitionRepository>()
      val baseConfig = AgileWorkItemFixtures.sampleConfig(tenantId)
      val configured = configuredProperty(tenantId, baseConfig.config.id, propertyId)
      val inactiveConfig =
        baseConfig.copy(
          config = baseConfig.config.copy(id = UUID.randomUUID(), isActive = false),
          properties = listOf(configured.copy(issueTypeConfigId = UUID.randomUUID())),
        )
      coEvery { configs.listConfigs(tenantId, projectId) } returns
        listOf(baseConfig.copy(properties = listOf(configured)), inactiveConfig)
      coEvery { properties.listProperties(tenantId) } returns
        listOf(
          propertyDefinition(tenantId, propertyId),
          propertyDefinition(tenantId, UUID.randomUUID()).copy(code = "inactive", isActive = false),
        )

      val fields = WorkItemDisplayFieldCatalogService(configs, properties).list(tenantId, projectId)

      fields.map { it.key } shouldContainExactly
        listOf(
          "key",
          "title",
          "issueType",
          "status",
          "priority",
          "assignee",
          "sprint",
          "updatedAt",
          "property.points",
        )
      fields.last().validation shouldBe
        JsonObject(mapOf("minimum" to JsonPrimitive(2), "maximum" to JsonPrimitive(20)))
      fields.last().array shouldBe false
    }
  })

class WorkItemFieldOptionServiceTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val scope = Triple(tenantId, projectId, actorId)
    val issue = mockk<WorkItemRecord>()
    val workItems = mockk<WorkItemRepository>()
    val users = mockk<UserRepository>()
    val contextLoader = mockk<WorkItemTransitionContextLoader>()
    val permissions = mockk<WorkItemFieldPermissionService>()
    val repository = mockk<WorkItemFieldOptionRepository>()
    val context = mockk<WorkItemTransitionContext>()
    val permissionContext = mockk<WorkItemFieldPermissionContext>()
    val captured = slot<WorkItemFieldOptionQuery>()
    val config = AgileWorkItemFixtures.sampleConfig(tenantId)
    val service =
      WorkItemFieldOptionService(workItems, users, contextLoader, permissions, repository)

    coEvery { workItems.findByApiId(tenantId, projectId, "issue-1") } returns issue
    coEvery { users.findById(actorId) } returns
      UserRecord(actorId, PublicId.new("usr"), "Actor", "actor@example.com")
    coEvery { contextLoader.load(issue, actorId, any(), any()) } returns context
    every { context.permissionContext } returns permissionContext
    every { context.config } returns config
    coEvery { permissions.resolvePatchPolicy(permissionContext, any()) } returns
      FieldMutationPolicy(FieldSubmissionPolicy.INHERIT_BINDING, bindingAllowsWrite = true)

    "paginates selectable options and returns an opaque cursor" {
      coEvery { repository.list(capture(captured)) } returns
        listOf(
          WorkItemFieldOption("user-1", "Alex"),
          WorkItemFieldOption("user-2", "Jordan"),
          WorkItemFieldOption("user-3", "Sam"),
        )

      val page = service.list(optionRequest(scope, "assignee", limit = 2))

      page.items.map { it.label } shouldContainExactly listOf("Alex", "Jordan")
      page.nextCursor shouldBe "Mg"
      captured.captured.kind shouldBe WorkItemFieldOptionKind.USER
      captured.captured.limit shouldBe 3
      captured.captured.offset shouldBe 0
    }

    "resolves configured fixed options and decodes the cursor" {
      val propertyId = UUID.randomUUID()
      every { context.config } returns
        config.copy(properties = listOf(configuredProperty(tenantId, config.config.id, propertyId)))
      coEvery { repository.list(capture(captured)) } returns emptyList()

      val page =
        service.list(
          optionRequest(
            scope,
            "property.points",
            cursor = "Mg",
          )
        )

      page.items shouldBe emptyList()
      page.nextCursor shouldBe null
      captured.captured.kind shouldBe WorkItemFieldOptionKind.FIXED
      captured.captured.propertyId shouldBe propertyId
      captured.captured.offset shouldBe 2
    }

    "rejects an option page outside the supported range" {
      shouldThrow<InvalidRequestException> {
        service.list(optionRequest(scope, "assignee", limit = 101))
      }
    }
  })

private fun optionRequest(
  scope: Triple<UUID, UUID, UUID>,
  fieldKey: String,
  cursor: String? = null,
  limit: Int = 50,
) =
  WorkItemFieldOptionsRequest(
    tenantId = scope.first,
    projectId = scope.second,
    workItemApiId = "issue-1",
    fieldKey = fieldKey,
    actorUserId = scope.third,
    page = WorkItemFieldOptionsPageRequest(search = "  al  ", cursor = cursor, limit = limit),
  )

private fun configuredProperty(
  tenantId: UUID,
  configId: UUID,
  propertyId: UUID,
) =
  IssueTypeConfigPropertyRecord(
    id = UUID.randomUUID(),
    tenantId = tenantId,
    issueTypeConfigId = configId,
    propertyId = propertyId,
    propertyApiId = PublicId.new("fld"),
    code = "points",
    name = "Story points",
    dataType = WorkItemPropertyDataType.SINGLE_SELECT,
    validationOverride = JsonObject(mapOf("minimum" to JsonPrimitive(2))),
    rank = 100,
    displayConfig = JsonObject(emptyMap()),
  )

private fun propertyDefinition(
  tenantId: UUID,
  propertyId: UUID,
): PropertyDefinitionRecord {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  return PropertyDefinitionRecord(
    id = propertyId,
    apiId = PublicId.new("fld"),
    tenantId = tenantId,
    code = "points",
    name = "Story points",
    description = null,
    dataType = WorkItemPropertyDataType.SINGLE_SELECT,
    isSystem = false,
    isArray = false,
    validationSchema =
      JsonObject(mapOf("minimum" to JsonPrimitive(1), "maximum" to JsonPrimitive(20))),
    searchConfig = JsonObject(emptyMap()),
    isActive = true,
    createdAt = now,
    updatedAt = now,
  )
}

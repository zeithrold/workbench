package ink.doa.workbench.agile.workitem

import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.port.messaging.DomainEventPublisher
import ink.doa.workbench.core.workitem.IssueTypeConfigRepository
import ink.doa.workbench.core.workitem.WorkItemRepository
import ink.doa.workbench.core.workitem.events.WorkItemDomainEvents
import ink.doa.workbench.core.workitem.model.WorkItemMutationResult
import ink.doa.workbench.core.workitem.model.WorkItemRecord
import ink.doa.workbench.core.workitem.model.WorkItemStatusGroup
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class WorkItemMutationSupportTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "publish emits created updated and transitioned events" {
      val events = mockk<DomainEventPublisher>()
      justRun { events.publish<Any>(any(), any(), any(), any()) }
      val support = WorkItemMutationSupport(mockk(), mockk(), events)
      val record = workItemRecord(tenantId, projectId)

      support.publish(WorkItemMutationResult(record, WorkItemDomainEvents.Created.type))
      support.publish(WorkItemMutationResult(record, WorkItemDomainEvents.Updated.type))
      support.publish(WorkItemMutationResult(record, WorkItemDomainEvents.Transitioned.type))
      support.publish(WorkItemMutationResult(record, "work_item.deleted"))

      verify(exactly = 3) { events.publish<Any>(any(), any(), any(), any()) }
    }

    "requireConfig throws when config is missing" {
      val configs = mockk<IssueTypeConfigRepository>()
      coEvery { configs.findConfig(tenantId, "missing") } returns null
      val support = WorkItemMutationSupport(mockk(), configs, mockk(relaxed = true))

      shouldThrow<ResourceNotFoundException> { support.requireConfig(tenantId, "missing") }
    }

    "templateContext resolves user and project api ids" {
      val repository = mockk<WorkItemRepository>()
      val userApiId = PublicId.new("usr")
      val projectApiId = PublicId.new("prj")
      coEvery { repository.resolveUserApiId(actorId) } returns userApiId
      coEvery { repository.resolveProjectApiId(tenantId, projectId) } returns projectApiId
      val support = WorkItemMutationSupport(repository, mockk(), mockk(relaxed = true))

      val context =
        support.templateContext(
          WorkItemTemplateContextRequest(
            tenantId = tenantId,
            projectId = projectId,
            actorUserId = actorId,
          )
        )

      context.currentUserApiId shouldBe userApiId.value
      context.currentProjectApiId shouldBe projectApiId.value
    }

    "templateContext throws when user is missing" {
      val repository = mockk<WorkItemRepository>()
      coEvery { repository.resolveUserApiId(actorId) } returns null
      val support = WorkItemMutationSupport(repository, mockk(), mockk(relaxed = true))

      shouldThrow<ResourceNotFoundException> {
        support.templateContext(
          WorkItemTemplateContextRequest(
            tenantId = tenantId,
            projectId = projectId,
            actorUserId = actorId,
          )
        )
      }
    }
  })

private fun workItemRecord(tenantId: UUID, projectId: UUID): WorkItemRecord {
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  return WorkItemRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("wki"),
    tenantId = tenantId,
    projectId = projectId,
    issueTypeApiId = PublicId.new("typ"),
    issueTypeConfigApiId = PublicId.new("itc"),
    key = "PROJ-1",
    title = "Task",
    description = null,
    statusId = UUID.randomUUID(),
    statusApiId = PublicId.new("sts"),
    statusGroup = WorkItemStatusGroup.TODO,
    reporterId = UUID.randomUUID(),
    assigneeId = null,
    priorityApiId = null,
    reporterApiId = PublicId.new("usr"),
    assigneeApiId = null,
    sprintApiId = null,
    properties = JsonObject(emptyMap()),
    createdAt = now,
    updatedAt = now,
  )
}

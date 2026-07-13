package ink.doa.workbench.agile.workitem.events

import ink.doa.workbench.agile.workitem.model.WorkItemRecord
import ink.doa.workbench.agile.workitem.model.WorkItemStatusGroup
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.kernel.messaging.DomainEventDecoder
import ink.doa.workbench.kernel.messaging.DomainEventEncoder
import ink.doa.workbench.kernel.messaging.EventMetadata
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class WorkItemMutationEventTest :
  StringSpec({
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val record =
      WorkItemRecord(
        id = UUID.randomUUID(),
        apiId = PublicId.new("wki"),
        tenantId = tenantId,
        projectId = projectId,
        issueTypeApiId = PublicId.new("typ"),
        issueTypeConfigApiId = PublicId.new("itc"),
        key = "PROJ-42",
        title = "Fix login",
        description =
          ink.doa.workbench.agile.workitem.richtext.RichTextProcessor.fromPlainText("Details"),
        statusId = UUID.randomUUID(),
        statusApiId = PublicId.new("sts"),
        statusGroup = WorkItemStatusGroup.IN_PROGRESS,
        reporterId = UUID.randomUUID(),
        assigneeId = UUID.randomUUID(),
        priorityApiId = PublicId.new("pri"),
        reporterApiId = PublicId.new("usr"),
        assigneeApiId = PublicId.new("usr"),
        sprintApiId = null,
        properties = JsonObject(emptyMap()),
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )

    "from maps work item record fields" {
      WorkItemMutationEvent.from(record) shouldBe
        WorkItemMutationEvent(
          tenantId = tenantId.toString(),
          projectId = projectId.toString(),
          workItemId = record.apiId.value,
          key = "PROJ-42",
          statusId = record.statusApiId.value,
          statusGroup = "in_progress",
        )
    }

    "domain event specs round-trip through codec" {
      val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
      val encoder = DomainEventEncoder(clock)
      val decoder = DomainEventDecoder()
      val payload = WorkItemMutationEvent.from(record)

      listOf(
          WorkItemDomainEvents.Created,
          WorkItemDomainEvents.Updated,
          WorkItemDomainEvents.Transitioned,
        )
        .forEach { spec ->
          val json =
            encoder.encode(
              spec,
              payload,
              EventMetadata(traceId = "trace-wi", tenantId = tenantId.toString()),
            )
          val envelope = decoder.parseEnvelope(json)
          envelope.type shouldBe spec.type
          decoder.decode(spec, envelope) shouldBe payload
        }
    }

    "rejects mismatched work item event type" {
      val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
      val encoder = DomainEventEncoder(clock)
      val decoder = DomainEventDecoder()
      val json = encoder.encode(WorkItemDomainEvents.Created, WorkItemMutationEvent.from(record))
      val envelope = decoder.parseEnvelope(json).copy(type = "work_item.deleted")
      shouldThrow<InvalidRequestException> {
        decoder.decode(WorkItemDomainEvents.Created, envelope)
      }
    }
  })

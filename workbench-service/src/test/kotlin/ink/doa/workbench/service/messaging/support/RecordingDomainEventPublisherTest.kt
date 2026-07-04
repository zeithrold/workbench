package ink.doa.workbench.service.messaging.support

import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.project.events.ProjectDestroyRequestedEvent
import ink.doa.workbench.core.project.events.ProjectDomainEvents
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class RecordingDomainEventPublisherTest :
  StringSpec({
    "publish records events and clear resets buffer" {
      val publisher = RecordingDomainEventPublisher()
      val payload =
        ProjectDestroyRequestedEvent(
          tenantId = "ten_test",
          projectId = "prj_test",
          requestedBy = "usr_test",
          deleteReason = "cleanup",
          requestedAt = "2026-07-04T00:00:00Z",
        )

      publisher.publish(
        spec = ProjectDomainEvents.DestroyRequested,
        key = "prj_test",
        payload = payload,
        metadata = EventMetadata(tenantId = "ten_test"),
      )

      publisher.published.single().key shouldBe "prj_test"
      publisher.published.single().payload shouldBe payload

      publisher.clear()
      publisher.published.shouldBeEmpty()
    }
  })

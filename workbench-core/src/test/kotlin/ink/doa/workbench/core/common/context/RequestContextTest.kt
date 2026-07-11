package ink.doa.workbench.core.common.context

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.UserRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class RequestContextTest :
  StringSpec({
    val receivedAt = Instant.parse("2026-07-04T00:00:00Z")
    val actor =
      UserContextSummary.from(
        UserRecord(UUID.randomUUID(), PublicId.new("usr"), "Ada", "ada@example.test")
      )
    val instance = InstanceContextSummary(id = "instance-1", name = "Workbench")
    val tenant =
      TenantContextSummary(
        id = UUID.randomUUID(),
        publicId = PublicId.new("ten"),
        slug = "acme",
        name = "Acme",
      )
    val project =
      ProjectContextSummary(
        id = UUID.randomUUID(),
        publicId = PublicId.new("prj"),
        identifier = "CORE",
        name = "Core Platform",
      )

    "api version accepts yyyy-MM-dd format" {
      ApiVersion("2026-07-03").value shouldBe "2026-07-03"
      ApiVersion.Default.value shouldBe "2026-07-11"
    }

    "api version rejects invalid format" {
      shouldThrow<IllegalArgumentException> { ApiVersion("v1") }
    }

    "request contexts carry scoped summaries" {
      val request =
        RequestContext(
          requestId = "req-1",
          apiVersion = ApiVersion.Default,
          actor = actor,
          receivedAt = receivedAt,
        )
      request.actor?.displayName shouldBe "Ada"

      val tenantContext =
        TenantRequestContext(
          requestId = "req-2",
          apiVersion = ApiVersion.Default,
          actor = actor,
          receivedAt = receivedAt,
          instance = instance,
          tenant = tenant,
        )
      tenantContext.tenant.slug shouldBe "acme"

      val projectContext =
        ProjectRequestContext(
          requestId = "req-3",
          apiVersion = ApiVersion.Default,
          actor = actor,
          receivedAt = receivedAt,
          instance = instance,
          tenant = tenant,
          project = project,
        )
      projectContext.project.identifier shouldBe "CORE"
    }

    "request host stores scheme and hostname" {
      RequestHost(scheme = "https", host = "acme.example.test").host shouldBe "acme.example.test"
    }
  })

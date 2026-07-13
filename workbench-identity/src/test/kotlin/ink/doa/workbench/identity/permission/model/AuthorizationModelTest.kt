package ink.doa.workbench.identity.permission.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class AuthorizationModelTest :
  StringSpec({
    "authorization resource builds canonical pattern" {
      val resource =
        AuthorizationResource(
          type = "project",
          id = "prj_01JABCDEFGHJKMNPQRSTVWXYZ0",
          tenantId = UUID.randomUUID(),
          projectId = UUID.randomUUID(),
        )

      resource.canonical shouldBe "project:prj_01JABCDEFGHJKMNPQRSTVWXYZ0"
    }

    "authorization environment stores request metadata" {
      val environment =
        AuthorizationEnvironment(
          requestId = "req-1",
          occurredAt = Instant.parse("2026-07-04T00:00:00Z"),
          attributes = mapOf("channel" to "api"),
        )

      environment.requestId shouldBe "req-1"
      environment.attributes["channel"] shouldBe "api"
    }
  })

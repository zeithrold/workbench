package one.ztd.workbench.identity.permission.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.ids.PublicId

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

    "authorization subject is built consistently from the principal" {
      val tenantId = UUID.randomUUID()
      val user =
        UserRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("usr"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
      val principal =
        AuthenticatedPrincipal(
          user = user,
          loginAccountId = UUID.randomUUID(),
          sessionId = "session-id",
          bearerTokenId = null,
          credentialScopes = setOf("workbench.api"),
        )

      val subject = AuthorizationSubject.from(principal, tenantId)

      subject.userId shouldBe user.id
      subject.userApiId shouldBe user.apiId.value
      subject.credentialId shouldBe "session-id"
      subject.credentialTenantId shouldBe tenantId
      subject.credentialScopes shouldBe setOf("workbench.api")
    }
  })

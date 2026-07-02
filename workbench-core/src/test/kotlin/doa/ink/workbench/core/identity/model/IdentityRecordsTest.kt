package doa.ink.workbench.core.identity.model

import doa.ink.workbench.core.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class IdentityRecordsTest :
  StringSpec({
    "authenticated principal can represent session authentication" {
      val user = UserRecord(UUID.randomUUID(), PublicId.new("usr"), "Ada", "ada@example.test")

      val principal =
        AuthenticatedPrincipal(user = user, sessionId = "session-1", bearerTokenId = null)

      principal.user.displayName shouldBe "Ada"
      principal.sessionId shouldBe "session-1"
    }
  })

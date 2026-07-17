package one.ztd.workbench.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.ids.PublicId

class UserPreferenceServiceTest :
  StringSpec({
    val clock = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC)
    val preferences = mockk<UserPreferenceRepository>()
    val service = UserPreferenceService(preferences, clock)
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
        sessionId = "session",
        bearerTokenId = null,
      )

    beforeTest { clearMocks(preferences) }

    "normalizes and persists a BCP 47 locale" {
      coEvery { preferences.updateLocale(user.id, "en-US", any()) } returns
        user.copy(locale = "en-US")

      runBlocking { service.updateLocale(principal, "en-us") }.locale shouldBe "en-US"
      coVerify {
        preferences.updateLocale(
          user.id,
          "en-US",
          OffsetDateTime.parse("2026-07-15T00:00:00Z"),
        )
      }
    }

    "clears the user override when locale is null" {
      coEvery { preferences.updateLocale(user.id, null, any()) } returns user.copy(locale = null)

      runBlocking { service.updateLocale(principal, null) }.locale shouldBe null
    }

    "rejects malformed locale tags" {
      shouldThrow<InvalidRequestException> {
          runBlocking { service.updateLocale(principal, "en_US") }
        }
        .errorCode shouldBe WorkbenchErrorCode.REQUEST_INVALID
      coVerify(exactly = 0) { preferences.updateLocale(any(), any(), any()) }
    }

    "reports a missing current user" {
      coEvery { preferences.updateLocale(user.id, "en-US", any()) } returns null

      shouldThrow<ResourceNotFoundException> {
          runBlocking { service.updateLocale(principal, "en-US") }
        }
        .errorCode shouldBe WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND
    }
  })

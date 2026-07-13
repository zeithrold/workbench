package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID

class UserLookupServiceTest :
  StringSpec({
    val users = mockk<UserRepository>()
    val service = UserLookupService(users)
    val userId = UUID.randomUUID()
    val user =
      UserRecord(
        id = userId,
        apiId = PublicId.new("usr"),
        displayName = "Ada",
        primaryEmail = "ada@example.test",
      )

    "requireUser returns user when found" {
      coEvery { users.findById(userId) } returns user

      service.requireUser(userId).displayName shouldBe "Ada"
    }

    "requireUser throws when user is missing" {
      coEvery { users.findById(userId) } returns null

      shouldThrow<ResourceNotFoundException> { service.requireUser(userId) }
    }

    "requireAuthenticatedUser throws invalid request when user is missing" {
      coEvery { users.findById(userId) } returns null

      shouldThrow<InvalidRequestException> { service.requireAuthenticatedUser(userId) }
    }
  })

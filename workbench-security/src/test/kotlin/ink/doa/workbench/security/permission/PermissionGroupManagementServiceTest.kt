package ink.doa.workbench.security.permission

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.core.permission.CreatePermissionGroupCommand
import ink.doa.workbench.core.permission.PermissionGroupRecord
import ink.doa.workbench.core.permission.PermissionGroupRepository
import ink.doa.workbench.security.common.PublicIdResolver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking

class PermissionGroupManagementServiceTest :
  StringSpec({
    val groups = mockk<PermissionGroupRepository>()
    val users = mockk<UserRepository>()
    val publicIds = mockk<PublicIdResolver>()
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val service = PermissionGroupManagementService(groups, users, publicIds, clock)
    val tenantId = UUID.randomUUID()

    "createGroup delegates to repository" {
      val group = sampleGroup(tenantId, "developers")
      coEvery {
        groups.create(
          CreatePermissionGroupCommand(
            tenantId = tenantId,
            code = "developers",
            name = "Developers",
            description = null,
          )
        )
      } returns group

      val result = runBlocking { service.createGroup(tenantId, "developers", "Developers", null) }

      result.code shouldBe "developers"
    }

    "updateGroup rejects builtin group changes" {
      val group = sampleGroup(tenantId, "tenant-admin", builtin = true)
      coEvery { groups.findByApiId(tenantId, group.apiId.value) } returns group

      shouldThrow<InvalidRequestException> {
        runBlocking { service.updateGroup(tenantId, group.apiId.value, "Renamed", null) }
      }
    }

    "addGroupMember resolves user and adds membership" {
      val group = sampleGroup(tenantId, "developers")
      val user = sampleUser()
      val member =
        ink.doa.workbench.core.permission.GroupMemberRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("pgm"),
          groupId = group.id,
          userId = user.id,
          status = ink.doa.workbench.core.permission.GroupMemberStatus.ACTIVE,
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      coEvery { groups.findByApiId(tenantId, group.apiId.value) } returns group
      coEvery { publicIds.resolveUser(user.apiId.value) } returns user
      coEvery { groups.addMember(any()) } returns member

      val result = runBlocking {
        service.addGroupMember(tenantId, group.apiId.value, user.apiId.value)
      }

      result.user.id shouldBe user.apiId.value
    }
  })

private fun sampleGroup(
  tenantId: UUID,
  code: String,
  builtin: Boolean = false,
): PermissionGroupRecord {
  val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
  return PermissionGroupRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("pgr"),
    tenantId = tenantId,
    code = code,
    name = code,
    description = null,
    builtin = builtin,
    createdAt = now,
    updatedAt = now,
  )
}

private fun sampleUser(): UserRecord =
  UserRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("usr"),
    displayName = "Ada",
    primaryEmail = "ada@example.test",
  )

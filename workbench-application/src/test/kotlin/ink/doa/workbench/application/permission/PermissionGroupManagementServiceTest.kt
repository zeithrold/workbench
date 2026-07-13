package ink.doa.workbench.application.permission

import ink.doa.workbench.application.identity.PublicIdResolver
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.identity.permission.CreatePermissionGroupCommand
import ink.doa.workbench.identity.permission.PermissionGroupRecord
import ink.doa.workbench.identity.permission.PermissionGroupRepository
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.ids.PublicId
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
        ink.doa.workbench.identity.permission.GroupMemberRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("pgm"),
          groupId = group.id,
          userId = user.id,
          status = ink.doa.workbench.identity.permission.GroupMemberStatus.ACTIVE,
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      coEvery { groups.findByApiId(tenantId, group.apiId.value) } returns group
      coEvery { publicIds.resolveUser(user.apiId.value) } returns user
      coEvery { groups.addMember(any()) } returns member

      val result = runBlocking {
        service.addGroupMember(tenantId, group.apiId.value, user.apiId.value)
      }

      result.user.id shouldBe user.apiId
    }

    "listGroups returns repository groups" {
      val group = sampleGroup(tenantId, "developers")
      coEvery { groups.list(tenantId) } returns listOf(group)

      val result = runBlocking { service.listGroups(tenantId) }

      result.single().code shouldBe "developers"
    }

    "getGroup returns group view" {
      val group = sampleGroup(tenantId, "developers")
      coEvery { groups.findByApiId(tenantId, group.apiId.value) } returns group

      val result = runBlocking { service.getGroup(tenantId, group.apiId.value) }

      result.code shouldBe "developers"
    }

    "deleteGroup rejects builtin group deletion" {
      val group = sampleGroup(tenantId, "tenant-admin", builtin = true)
      coEvery { groups.findByApiId(tenantId, group.apiId.value) } returns group

      shouldThrow<InvalidRequestException> {
        runBlocking { service.deleteGroup(tenantId, group.apiId.value) }
      }
    }

    "deleteGroup delegates to repository for custom group" {
      val group = sampleGroup(tenantId, "developers")
      coEvery { groups.findByApiId(tenantId, group.apiId.value) } returns group
      coEvery { groups.delete(tenantId, group.id) } returns true

      runBlocking { service.deleteGroup(tenantId, group.apiId.value) } shouldBe true
    }

    "listGroupMembers resolves user summaries" {
      val group = sampleGroup(tenantId, "developers")
      val user = sampleUser()
      val member =
        ink.doa.workbench.identity.permission.GroupMemberRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("pgm"),
          groupId = group.id,
          userId = user.id,
          status = ink.doa.workbench.identity.permission.GroupMemberStatus.ACTIVE,
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      coEvery { groups.findByApiId(tenantId, group.apiId.value) } returns group
      coEvery { groups.listMembers(group.id) } returns listOf(member)
      coEvery { users.findById(user.id) } returns user

      val result = runBlocking { service.listGroupMembers(tenantId, group.apiId.value) }

      result.single().user.id shouldBe user.apiId
    }

    "removeGroupMember resolves user and removes membership" {
      val group = sampleGroup(tenantId, "developers")
      val user = sampleUser()
      coEvery { groups.findByApiId(tenantId, group.apiId.value) } returns group
      coEvery { publicIds.resolveUser(user.apiId.value) } returns user
      coEvery { groups.removeMember(group.id, user.id, any()) } returns true

      runBlocking {
        service.removeGroupMember(tenantId, group.apiId.value, user.apiId.value)
      } shouldBe true
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

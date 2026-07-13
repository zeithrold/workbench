package ink.doa.workbench.data.repository.permission

import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.repository.identity.ExposedUserRepository
import ink.doa.workbench.data.support.withCorePostgresDatabase
import ink.doa.workbench.identity.model.CreateUserCommand
import ink.doa.workbench.identity.permission.AdminScope
import ink.doa.workbench.identity.permission.AdminUserStatus
import ink.doa.workbench.identity.permission.CreateAdminUserCommand
import ink.doa.workbench.kernel.common.ids.PublicId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ExposedAdminUserRepositoriesIntegrationTest :
  StringSpec({
    "admin user command and query repositories manage instance and tenant admins" {
      withCorePostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val commands = ExposedAdminUserCommandRepository(database)
        val queries = ExposedAdminUserQueryRepository(database)
        val user = users.create(CreateUserCommand("Admin", "admin@example.test"))
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val instanceAdmin =
          commands.create(
            CreateAdminUserCommand(
              userId = user.id,
              scope = AdminScope.INSTANCE,
              tenantId = null,
              grantedBy = user.id,
              validFrom = now.minusMinutes(1),
              validTo = null,
            )
          )
        val tenantAdmin =
          commands.create(
            CreateAdminUserCommand(
              userId = user.id,
              scope = AdminScope.TENANT,
              tenantId = tenantId,
              grantedBy = user.id,
              validFrom = now.minusMinutes(1),
              validTo = null,
            )
          )

        instanceAdmin.status shouldBe AdminUserStatus.ACTIVE
        queries.findById(instanceAdmin.id).shouldNotBeNull()
        queries.findByApiId(instanceAdmin.apiId.value)?.userId shouldBe user.id
        queries.existsActiveInstanceAdmin() shouldBe true
        queries.isActiveInstanceAdmin(user.id, now) shouldBe true
        queries.isActiveTenantAdmin(tenantId, user.id, now) shouldBe true
        queries.listByUser(user.id).shouldHaveSize(2)
        queries.listInstanceAdmins().single().id shouldBe instanceAdmin.id
        queries.listTenantAdmins(tenantId).single().id shouldBe tenantAdmin.id

        commands.revoke(instanceAdmin.id, now) shouldBe true
        queries.isActiveInstanceAdmin(user.id, now) shouldBe false

        commands.revokeByTenant(tenantId, now) shouldBe 1
        queries.isActiveTenantAdmin(tenantId, user.id, now) shouldBe false
      }
    }

    "findActiveInstanceAdmin returns null when admin is revoked" {
      withCorePostgresDatabase { database ->
        val users = ExposedUserRepository(database)
        val commands = ExposedAdminUserCommandRepository(database)
        val queries = ExposedAdminUserQueryRepository(database)
        val user = users.create(CreateUserCommand("Revoked", "revoked@example.test"))
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val admin =
          commands.create(
            CreateAdminUserCommand(
              userId = user.id,
              scope = AdminScope.INSTANCE,
              tenantId = null,
              grantedBy = user.id,
              validFrom = now.minusMinutes(1),
              validTo = null,
            )
          )

        commands.revoke(admin.id, now)
        queries.findActiveInstanceAdmin(user.id, now).shouldBeNull()
      }
    }
  })

private fun seedTenant(database: Database): UUID {
  val tenantId = UUID.randomUUID()
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  transaction(database) {
    TenantsTable.insert {
      it[id] = tenantId.toKotlinUuid()
      it[apiId] = PublicId.new("ten").value
      it[name] = "Test Tenant"
      it[slug] = "test-${tenantId.toString().take(8)}"
      it[timezone] = "UTC"
      it[locale] = "en-US"
      it[createdAt] = now
      it[updatedAt] = now
    }
  }
  return tenantId
}

package ink.doa.workbench.data.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.CreateUserCommand
import ink.doa.workbench.core.permission.AddGroupMemberCommand
import ink.doa.workbench.core.permission.CreatePermissionBindingCommand
import ink.doa.workbench.core.permission.CreatePermissionGroupCommand
import ink.doa.workbench.core.permission.CreatePermissionPolicyCommand
import ink.doa.workbench.core.permission.CreatePermissionPolicyRuleCommand
import ink.doa.workbench.core.permission.PermissionPrincipalType
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.data.identity.ExposedUserRepository
import ink.doa.workbench.data.persistence.TenantsTable
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer

class ExposedPermissionManagementRepositoriesTest :
  StringSpec({
    "group bindings resolve active member policy rules" {
      withPermissionPostgresDatabase { database ->
        val tenantId = seedTenant(database)
        val users = ExposedUserRepository(database)
        val groups = ExposedPermissionGroupRepository(database)
        val policies = ExposedPermissionPolicyRepository(database)
        val bindings = ExposedPermissionBindingRepository(database)
        val user = users.create(CreateUserCommand("Ada", "ada-permissions@example.test"))
        val group =
          groups.create(
            CreatePermissionGroupCommand(
              tenantId = tenantId,
              code = "developers",
              name = "Developers",
              description = null,
            )
          )
        groups.addMember(AddGroupMemberCommand(group.id, user.id))
        val policy =
          policies.create(
            CreatePermissionPolicyCommand(
              tenantId = tenantId,
              code = "project-reader",
              name = "Project Reader",
              description = null,
            )
          )
        policies.addRule(
          CreatePermissionPolicyRuleCommand(
            policyId = policy.id,
            action = AuthorizationAction("project.read"),
            resourcePattern = "project:*",
          )
        )
        bindings.create(
          CreatePermissionBindingCommand(
            tenantId = tenantId,
            projectId = null,
            principalType = PermissionPrincipalType.GROUP,
            principalUserId = null,
            principalGroupId = group.id,
            policyId = policy.id,
            validFrom = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
            createdBy = user.id,
          )
        )

        val rules =
          bindings.listActiveRulesForSubject(
            subjectUserId = user.id,
            tenantId = tenantId,
            projectId = null,
            at = OffsetDateTime.now(ZoneOffset.UTC),
          )
        rules.shouldHaveSize(1)
        rules.single().action.code shouldBe "project.read"

        groups.removeMember(group.id, user.id, OffsetDateTime.now(ZoneOffset.UTC))
        bindings
          .listActiveRulesForSubject(
            subjectUserId = user.id,
            tenantId = tenantId,
            projectId = null,
            at = OffsetDateTime.now(ZoneOffset.UTC),
          )
          .shouldBeEmpty()
      }
    }
  })

private fun withPermissionPostgresDatabase(block: suspend (Database) -> Unit) {
  PostgreSQLContainer("postgres:18-alpine").use { postgres ->
    postgres.start()
    Flyway.configure()
      .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
      .locations("classpath:db/migration")
      .load()
      .migrate()
    val database =
      Database.connect(
        url = postgres.jdbcUrl,
        driver = "org.postgresql.Driver",
        user = postgres.username,
        password = postgres.password,
      )
    runBlocking { block(database) }
  }
}

private fun seedTenant(database: Database): UUID {
  val tenantId = UUID.randomUUID()
  val now = OffsetDateTime.now(ZoneOffset.UTC)
  transaction(database) {
    TenantsTable.insert {
      it[id] = tenantId.toKotlinUuid()
      it[apiId] = PublicId.new("ten").value
      it[name] = "Test Tenant"
      it[slug] = "permission-${tenantId.toString().take(8)}"
      it[timezone] = "UTC"
      it[locale] = "en-US"
      it[createdAt] = now
      it[updatedAt] = now
    }
  }
  return tenantId
}

package one.ztd.workbench.data.repository.permission

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import one.ztd.workbench.data.support.withPostgresDatabase
import one.ztd.workbench.identity.permission.CreatePermissionActionCommand
import one.ztd.workbench.identity.permission.model.AuthorizationAction

class ExposedPermissionActionRepositoryIntegrationTest :
  StringSpec({
    "upsert inserts new action and returns existing on duplicate" {
      withPostgresDatabase { database ->
        val repository = ExposedPermissionActionRepository(database)
        val code = AuthorizationAction("test.coverage.action")

        val created = repository.upsert(CreatePermissionActionCommand(code, "Coverage test action"))
        created.code shouldBe code
        created.description shouldBe "Coverage test action"

        val existing = repository.upsert(CreatePermissionActionCommand(code, "Should not update"))
        existing.id shouldBe created.id
        existing.description shouldBe "Coverage test action"

        repository.findByCode(code).shouldNotBeNull()
        repository.list().shouldNotBeEmpty()
      }
    }

    "findByCode returns seeded permission actions" {
      withPostgresDatabase { database ->
        val repository = ExposedPermissionActionRepository(database)

        repository.findByCode(AuthorizationAction("project.read")).shouldNotBeNull()
      }
    }
  })

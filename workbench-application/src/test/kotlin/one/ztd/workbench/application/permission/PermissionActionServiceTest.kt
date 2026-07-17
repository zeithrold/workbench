package one.ztd.workbench.application.permission

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.identity.permission.CreatePermissionActionCommand
import one.ztd.workbench.identity.permission.PermissionActionRecord
import one.ztd.workbench.identity.permission.PermissionActionRepository
import one.ztd.workbench.identity.permission.model.AuthorizationAction

class PermissionActionServiceTest :
  StringSpec({
    val actions = mockk<PermissionActionRepository>()
    val service = PermissionActionService(actions)
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "listActions maps repository records to views" {
      val record =
        PermissionActionRecord(
          id = UUID.randomUUID(),
          code = AuthorizationAction("project.read"),
          description = "Read projects",
          createdAt = now,
        )
      coEvery { actions.list() } returns listOf(record)

      service.listActions().single().code shouldBe "project.read"
    }

    "ensureAction upserts and returns action view" {
      val record =
        PermissionActionRecord(
          id = UUID.randomUUID(),
          code = AuthorizationAction("workitem.read"),
          description = "Read work items",
          createdAt = now,
        )
      coEvery {
        actions.upsert(
          CreatePermissionActionCommand(AuthorizationAction("workitem.read"), "Read work items")
        )
      } returns record

      service.ensureAction("workitem.read", "Read work items").description shouldBe
        "Read work items"
      coVerify {
        actions.upsert(
          CreatePermissionActionCommand(AuthorizationAction("workitem.read"), "Read work items")
        )
      }
    }

    "listTenantCapabilities excludes Agile actions" {
      coEvery { actions.list() } returns
        listOf(
          PermissionActionRecord(
            id = UUID.randomUUID(),
            code = AuthorizationAction("issue.view"),
            description = "View issues",
            createdAt = now,
          ),
          PermissionActionRecord(
            id = UUID.randomUUID(),
            code = AuthorizationAction("tenant.read"),
            description = "Read tenants",
            createdAt = now,
          ),
        )

      service.listTenantCapabilities().map { it.action } shouldBe listOf("tenant.read")
    }
  })

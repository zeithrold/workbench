package ink.doa.workbench.application.permission

import ink.doa.workbench.identity.permission.CreatePermissionActionCommand
import ink.doa.workbench.identity.permission.PermissionActionRecord
import ink.doa.workbench.identity.permission.PermissionActionRepository
import ink.doa.workbench.identity.permission.model.AuthorizationAction
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

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
  })

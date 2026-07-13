package ink.doa.workbench.identity.common.summary

import ink.doa.workbench.agile.project.ProjectSummary
import ink.doa.workbench.agile.project.model.ProjectRecord
import ink.doa.workbench.agile.project.model.ProjectStatus
import ink.doa.workbench.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.tenant.common.summary.TenantSummary
import ink.doa.workbench.tenant.model.TenantRecord
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.util.UUID

class SummaryRecordsTest :
  StringSpec({
    val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
    val tenantId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    val projectId = UUID.randomUUID()

    "tenant summary maps from tenant record" {
      val record =
        TenantRecord(
          id = tenantId,
          apiId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          slug = "acme",
          name = "Acme",
          timezone = "UTC",
          locale = "en-US",
          createdAt = now,
          updatedAt = now,
        )

      TenantSummary.from(record) shouldBe
        TenantSummary(
          id = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          name = "Acme",
          slug = "acme",
        )
    }

    "user summary maps from user record" {
      val record =
        UserRecord(
          id = userId,
          apiId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )

      UserSummary.from(record) shouldBe
        UserSummary(
          id = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
    }

    "project summary maps from project record" {
      val record =
        ProjectRecord(
          id = projectId,
          apiId = PublicId("prj_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          tenantId = tenantId,
          identifier = "CORE",
          name = "Core Platform",
          description = null,
          status = ProjectStatus.ACTIVE,
          leadUserId = userId,
          createdBy = userId,
        )

      ProjectSummary.from(record) shouldBe
        ProjectSummary(
          id = PublicId("prj_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          identifier = "CORE",
          name = "Core Platform",
        )
    }

    "login method summary maps from definition record" {
      val record =
        LoginMethodDefinitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId("lmd_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          code = "password",
          kind = LoginMethodKind.PASSWORD,
          name = "Password",
          isBuiltin = true,
          isEnabledGlobally = true,
          configSchema = kotlinx.serialization.json.JsonObject(emptyMap()),
          createdAt = now,
          updatedAt = now,
        )

      LoginMethodSummary.from(record).code shouldBe "password"
    }
  })

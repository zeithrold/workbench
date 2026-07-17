package one.ztd.workbench.kernel.common.warning

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import one.ztd.workbench.agile.project.ProjectSummary
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.kernel.common.warning.meta.ApiVersionDeprecatedMeta
import one.ztd.workbench.kernel.common.warning.meta.ProjectDestroyScheduledMeta
import one.ztd.workbench.kernel.common.warning.meta.TenantDestroyScheduledMeta
import one.ztd.workbench.kernel.common.warning.meta.WarningTruncatedMeta
import one.ztd.workbench.kernel.common.warning.meta.WorkbenchWarningMeta
import one.ztd.workbench.tenant.common.summary.TenantSummary

class WorkbenchWarningMetaValidatorTest :
  StringSpec({
    val project =
      ProjectSummary(
        id = PublicId("prj_01JABCDEFGHJKMNPQRSTVWXYZ0"),
        identifier = "WB",
        name = "Workbench",
      )

    "validate accepts matching code and meta" {
      WorkbenchWarningMetaValidator.validate(
        WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
        ProjectDestroyScheduledMeta(project = project, deleteReason = "cleanup"),
      )
    }

    "validate rejects mismatched meta kind" {
      shouldThrow<IllegalArgumentException> {
        WorkbenchWarningMetaValidator.validate(
          WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
          WarningTruncatedMeta,
        )
      }
    }

    "validate rejects matching kind with wrong meta class" {
      val meta = mockk<WorkbenchWarningMeta>()
      every { meta.kind } returns "projectDestroyScheduled"

      shouldThrow<IllegalArgumentException> {
        WorkbenchWarningMetaValidator.validate(
          WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
          meta,
        )
      }
    }

    "validate rejects blank project identifier" {
      shouldThrow<IllegalArgumentException> {
        WorkbenchWarningMetaValidator.validate(
          WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
          ProjectDestroyScheduledMeta(project = project.copy(identifier = "  ")),
        )
      }
    }

    "validate rejects blank project name" {
      shouldThrow<IllegalArgumentException> {
        WorkbenchWarningMetaValidator.validate(
          WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED,
          ProjectDestroyScheduledMeta(project = project.copy(name = "")),
        )
      }
    }

    "validatePublicId rejects wrong prefix" {
      shouldThrow<IllegalArgumentException> {
        WorkbenchWarningMetaValidator.validatePublicId(
          "usr_01JABCDEFGHJKMNPQRSTVWXYZ0",
          "prj",
        )
      }
    }

    "validateUserRef accepts usr public id" {
      WorkbenchWarningMetaValidator.validateUserRef("usr_01JABCDEFGHJKMNPQRSTVWXYZ0")
    }

    "validate accepts tenant destroy scheduled meta" {
      WorkbenchWarningMetaValidator.validate(
        WorkbenchWarningCode.TENANT_DESTROY_SCHEDULED,
        TenantDestroyScheduledMeta(
          tenant =
            TenantSummary(
              id = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ1"),
              name = "Acme",
              slug = "acme",
            )
        ),
      )
    }

    "validate rejects blank tenant name" {
      shouldThrow<IllegalArgumentException> {
        WorkbenchWarningMetaValidator.validate(
          WorkbenchWarningCode.TENANT_DESTROY_SCHEDULED,
          TenantDestroyScheduledMeta(
            tenant =
              TenantSummary(
                id = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ1"),
                name = " ",
                slug = "acme",
              )
          ),
        )
      }
    }

    "validate accepts api version deprecated meta" {
      WorkbenchWarningMetaValidator.validate(
        WorkbenchWarningCode.API_VERSION_DEPRECATED,
        ApiVersionDeprecatedMeta(
          requestedVersion = "2024-01-01",
          currentVersion = "2024-06-01",
          sunsetOn = "2025-01-01",
        ),
      )
    }

    "validate rejects invalid api version format" {
      shouldThrow<IllegalArgumentException> {
        WorkbenchWarningMetaValidator.validate(
          WorkbenchWarningCode.API_VERSION_DEPRECATED,
          ApiVersionDeprecatedMeta(
            requestedVersion = "v1",
            currentVersion = "2024-06-01",
            sunsetOn = null,
          ),
        )
      }
    }

    "validate rejects invalid api sunset date" {
      shouldThrow<IllegalArgumentException> {
        WorkbenchWarningMetaValidator.validate(
          WorkbenchWarningCode.API_VERSION_DEPRECATED,
          ApiVersionDeprecatedMeta(
            requestedVersion = "2024-01-01",
            currentVersion = "2024-06-01",
            sunsetOn = "next-year",
          ),
        )
      }
    }

    "tenant and api version meta DTOs expose expected kinds" {
      TenantDestroyScheduledMeta(
          tenant =
            TenantSummary(
              id = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ1"),
              name = "Acme",
              slug = "acme",
            ),
          deleteReason = "cleanup",
        )
        .kind shouldBe "tenantDestroyScheduled"

      ApiVersionDeprecatedMeta(
          requestedVersion = "2024-01-01",
          currentVersion = "2024-06-01",
          sunsetOn = "2025-01-01",
        )
        .kind shouldBe "apiVersionDeprecated"
    }

    "collector deduplicates identical warnings" {
      val collector = InMemoryWorkbenchWarningCollector()
      val meta = ProjectDestroyScheduledMeta(project = project)

      collector.warn(WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED, meta)
      collector.warn(WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED, meta)

      collector.drain() shouldHaveSize 1
    }

    "collector drain clears stored warnings" {
      val collector = InMemoryWorkbenchWarningCollector()
      val meta = ProjectDestroyScheduledMeta(project = project)

      collector.warn(WorkbenchWarningCode.PROJECT_DESTROY_SCHEDULED, meta)
      collector.drain() shouldHaveSize 1
      collector.drain() shouldHaveSize 0
    }

    "collector warn overload uses custom message" {
      val collector = InMemoryWorkbenchWarningCollector()
      val meta =
        TenantDestroyScheduledMeta(
          tenant =
            TenantSummary(
              id = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ1"),
              name = "Acme",
              slug = "acme",
            )
        )

      collector.warn(
        WorkbenchWarningCode.TENANT_DESTROY_SCHEDULED,
        meta,
        message = "Tenant teardown queued",
      )

      val drained = collector.drain()
      drained shouldHaveSize 1
      drained.single().message shouldBe "Tenant teardown queued"
    }
  })

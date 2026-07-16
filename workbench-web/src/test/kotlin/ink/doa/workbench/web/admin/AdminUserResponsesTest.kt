package ink.doa.workbench.web.admin

import ink.doa.workbench.application.permission.AccessGrantView
import ink.doa.workbench.application.permission.AdminUserView
import ink.doa.workbench.application.permission.TenantPermissionCapability
import ink.doa.workbench.identity.permission.AdminScope
import ink.doa.workbench.identity.permission.GrantScope
import ink.doa.workbench.identity.permission.model.PermissionEffect
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.web.api.ProblemDetailSupport
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import org.springframework.http.HttpStatus

class AdminUserResponsesTest :
  StringSpec({
    "admin user response maps view fields" {
      val view =
        AdminUserView(
          id = "adm_abc",
          userId = "usr_abc",
          scope = AdminScope.TENANT,
          tenantId = "ten_abc",
          status = "active",
          validFrom = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          validTo = null,
        )

      AdminUserResponse.from(view).scope shouldBe "tenant"
    }

    "access grant response maps view fields" {
      val view =
        AccessGrantView(
          id = "agr_abc",
          scope = GrantScope.TENANT,
          tenantId = "ten_abc",
          projectId = null,
          userId = "usr_abc",
          action = "project.read",
          resourcePattern = "project:*",
          effect = PermissionEffect.ALLOW,
          validFrom = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          validTo = null,
        )

      AccessGrantResponse.from(view).action shouldBe "project.read"
    }

    "action response maps view fields" {
      ActionResponse.from(
          TenantPermissionCapability(
            action = "tenant.read",
            resourcePattern = "tenant:*",
            name = "View tenant settings",
            description = "Read tenant settings",
          )
        )
        .code shouldBe "tenant.read"
    }

    "problem detail support attaches error code" {
      val problem =
        ProblemDetailSupport.problem(
          HttpStatus.BAD_REQUEST,
          "Invalid Request",
          InvalidRequestException(WorkbenchErrorCode.REQUEST_INVALID, "bad input"),
        )

      problem.status shouldBe 400
      problem.properties?.get("code") shouldBe WorkbenchErrorCode.REQUEST_INVALID.code
    }
  })

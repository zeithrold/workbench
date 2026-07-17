package one.ztd.workbench.web.admin

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import one.ztd.workbench.application.permission.AccessGrantView
import one.ztd.workbench.application.permission.AdminUserView
import one.ztd.workbench.application.permission.TenantPermissionCapability
import one.ztd.workbench.identity.permission.AdminScope
import one.ztd.workbench.identity.permission.GrantScope
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.web.api.ProblemDetailSupport
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

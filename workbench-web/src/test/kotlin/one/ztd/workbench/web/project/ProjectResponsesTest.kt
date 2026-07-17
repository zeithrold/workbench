package one.ztd.workbench.web.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.agile.project.ProjectMemberPolicyView
import one.ztd.workbench.agile.project.ProjectMemberView
import one.ztd.workbench.agile.project.ProjectPermissionPolicySummary
import one.ztd.workbench.application.project.ProjectView
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.kernel.common.ids.PublicId

class ProjectResponsesTest :
  StringSpec({
    "project response maps service view" {
      val view =
        ProjectView(
          id = "prj_01JABCDEFGHJKMNPQRSTVWXYZ0",
          identifier = "CORE",
          name = "Core Platform",
          description = "Platform",
          status = "active",
          nonMemberVisibility = "invisible",
          nonMemberJoinPolicy = "admin_only",
          lead =
            UserSummary(
              id = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
              displayName = "Ada",
              primaryEmail = "ada@example.test",
            ),
          archivedAt = null,
        )

      ProjectResponse.from(view).identifier shouldBe "CORE"
    }

    "project member response maps nested policies" {
      val view =
        ProjectMemberView(
          user =
            UserSummary(
              id = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
              displayName = "Ada",
              primaryEmail = "ada@example.test",
            ),
          policies =
            listOf(
              ProjectMemberPolicyView(
                bindingId = "bnd_01JABCDEFGHJKMNPQRSTVWXYZ0",
                policy =
                  ProjectPermissionPolicySummary(
                    id = "pol_01JABCDEFGHJKMNPQRSTVWXYZ0",
                    code = "member",
                    name = "Member",
                  ),
              )
            ),
        )

      val response = ProjectMemberResponse.from(view)
      response.user.displayName shouldBe "Ada"
      response.policies.single().policy.code shouldBe "member"
    }

    "permission policy summary response maps summary fields" {
      val summary =
        ProjectPermissionPolicySummary(
          id = "pol_01JABCDEFGHJKMNPQRSTVWXYZ0",
          code = "member",
          name = "Member",
        )

      PermissionPolicySummaryResponse.from(summary).name shouldBe "Member"
    }
  })

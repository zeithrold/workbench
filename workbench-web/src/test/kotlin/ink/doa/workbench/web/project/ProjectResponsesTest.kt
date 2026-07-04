package ink.doa.workbench.web.project

import ink.doa.workbench.agile.project.ProjectMemberPolicyView
import ink.doa.workbench.agile.project.ProjectMemberView
import ink.doa.workbench.agile.project.ProjectPermissionPolicySummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.service.project.ProjectView
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

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
              id = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
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
              id = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
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
  })

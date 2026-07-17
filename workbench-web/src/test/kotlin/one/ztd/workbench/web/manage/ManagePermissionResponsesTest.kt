package one.ztd.workbench.web.manage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.agile.project.ProjectSummary
import one.ztd.workbench.application.permission.GroupMemberView
import one.ztd.workbench.application.permission.PermissionBindingView
import one.ztd.workbench.application.permission.PermissionGroupView
import one.ztd.workbench.application.permission.PermissionPolicyRuleView
import one.ztd.workbench.application.permission.PermissionPolicySummary
import one.ztd.workbench.application.permission.PermissionPolicyView
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.permission.PermissionPrincipalType
import one.ztd.workbench.kernel.common.ids.PublicId

class ManagePermissionResponsesTest :
  StringSpec({
    "permission group response maps view fields" {
      val view =
        PermissionGroupView(
          id = "pgr_abc",
          code = "developers",
          name = "Developers",
          description = null,
          builtin = false,
        )

      PermissionGroupResponse.from(view).code shouldBe "developers"
    }

    "group member response maps view fields" {
      val user =
        UserSummary(
          id = PublicId.new("usr"),
          displayName = "Ada",
          primaryEmail = "ada@example.test",
        )
      val view = GroupMemberView(id = "pgm_abc", user = user)

      GroupMemberResponse.from(view).user.displayName shouldBe "Ada"
    }

    "permission policy response maps nested rules" {
      val view =
        PermissionPolicyView(
          id = "pol_abc",
          code = "project-admin",
          name = "Project Admin",
          description = null,
          builtin = true,
          rules =
            listOf(
              PermissionPolicyRuleView(
                action = "project.manage",
                resourcePattern = "project:*",
                effect = "ALLOW",
                condition = """{"field":"statusGroup","op":"eq","value":"todo"}""",
              )
            ),
        )

      val response = PermissionPolicyResponse.from(view)
      response.rules.single().action shouldBe "project.manage"
      response.rules.single().condition?.get("field") shouldBe "statusGroup"
    }

    "permission binding response maps nested summaries" {
      val view =
        PermissionBindingView(
          id = "pbd_abc",
          principalType = "user",
          user = UserSummary(id = PublicId.new("usr"), displayName = "Ada", primaryEmail = null),
          group = null,
          policy =
            PermissionPolicySummary(id = "pol_abc", code = "project-admin", name = "Project Admin"),
          project = ProjectSummary(id = PublicId.new("prj"), identifier = "CORE", name = "Core"),
        )

      PermissionBindingResponse.from(view).project?.identifier shouldBe "CORE"
    }

    "create permission binding request stores principal fields" {
      val request =
        CreatePermissionBindingRequest(
          principalType = PermissionPrincipalType.USER,
          userId = "usr_abc",
          groupId = null,
          policyId = "pol_abc",
        )

      request.policyId shouldBe "pol_abc"
    }

    "add group member request stores user id" {
      AddGroupMemberRequest(userId = "usr_abc").userId shouldBe "usr_abc"
    }
  })

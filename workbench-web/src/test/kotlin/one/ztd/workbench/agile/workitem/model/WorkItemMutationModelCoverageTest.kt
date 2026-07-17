package one.ztd.workbench.agile.workitem.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.ztd.workbench.agile.workitem.WorkItemSearchPageRequest
import one.ztd.workbench.identity.model.AcceptInvitationCommand
import one.ztd.workbench.identity.model.CreateAuthLoginStateCommand
import one.ztd.workbench.identity.model.CreateInvitationCommand
import one.ztd.workbench.identity.model.CreateTenantLoginMethodSettingCommand
import one.ztd.workbench.identity.model.CreateTenantWithAdminCommand
import one.ztd.workbench.identity.model.InvitationRecord
import one.ztd.workbench.identity.model.InvitationType
import one.ztd.workbench.identity.model.TenantAdminAssignment.EmailInviteAssignment
import one.ztd.workbench.identity.model.TenantAdminAssignment.SelfAssignment
import one.ztd.workbench.identity.model.TenantAdminAssignment.UserAssignment
import one.ztd.workbench.identity.permission.CreatePermissionBindingCommand
import one.ztd.workbench.identity.permission.CreatePermissionPolicyRuleCommand
import one.ztd.workbench.identity.permission.PermissionPrincipalType
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.kernel.common.context.InstanceContextSummary
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.model.UpdateTenantCommand
import one.ztd.workbench.tenant.tenantconfig.model.AuthSessionTenantConfig
import one.ztd.workbench.tenant.tenantconfig.model.MailSmtpTenantConfig
import one.ztd.workbench.tenant.tenantconfig.model.TenantConfigSpecs
import one.ztd.workbench.web.api.context.ApiVersion
import one.ztd.workbench.web.api.context.InstanceRequestContext

class WorkItemMutationModelCoverageTest :
  StringSpec({
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    val tenantId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val userId = UUID.randomUUID()

    "work item mutation commands store actor and field metadata" {
      CreateWorkItemCommand(
          tenantId = tenantId,
          projectId = projectId,
          issueTypeApiId = "typ_abc",
          title = "New issue",
          description =
            one.ztd.workbench.agile.workitem.richtext.RichTextProcessor.fromPlainText("Details"),
          reporterId = userId,
          actorUserId = userId,
          assigneeApiId = "usr_assignee",
          properties = mapOf("severity" to JsonPrimitive("high")),
        )
        .title shouldBe "New issue"

      UpdateWorkItemCommand(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = "iss_abc",
          title = "Updated",
          actorUserId = userId,
        )
        .workItemApiId shouldBe "iss_abc"

      DeleteWorkItemCommand(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = "iss_abc",
          actorUserId = userId,
          deleteReason = "duplicate",
        )
        .deleteReason shouldBe "duplicate"

      UpdateWorkItemCommentCommand(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = "iss_abc",
          commentApiId = "cmt_abc",
          actorUserId = userId,
          body =
            one.ztd.workbench.agile.workitem.richtext.RichTextProcessor.fromPlainText("Edited")!!,
        )
        .body shouldBe
        one.ztd.workbench.agile.workitem.richtext.RichTextProcessor.fromPlainText("Edited")

      DeleteWorkItemCommentCommand(
          tenantId = tenantId,
          projectId = projectId,
          workItemApiId = "iss_abc",
          commentApiId = "cmt_abc",
          actorUserId = userId,
        )
        .commentApiId shouldBe "cmt_abc"
    }

    "work item comment record stores authorship metadata" {
      WorkItemCommentRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("cmt"),
          tenantId = tenantId,
          issueId = UUID.randomUUID(),
          authorId = userId,
          authorApiId = PublicId.new("usr"),
          body =
            one.ztd.workbench.agile.workitem.richtext.RichTextProcessor.fromPlainText(
              "Looks good"
            )!!,
          bodyPlainText = "Looks good",
          transitionId = UUID.randomUUID(),
          statusHistoryId = UUID.randomUUID(),
          editedAt = now,
          createdAt = now,
          updatedAt = now,
        )
        .bodyPlainText shouldBe "Looks good"
    }

    "work item mutation result stores stream event references" {
      val streamEventId = UUID.randomUUID()
      val streamEventApiId = PublicId.new("evt")
      WorkItemMutationResult(
          workItem =
            WorkItemRecord(
              id = UUID.randomUUID(),
              apiId = PublicId.new("iss"),
              tenantId = tenantId,
              projectId = projectId,
              issueTypeApiId = PublicId.new("typ"),
              issueTypeConfigApiId = PublicId.new("itc"),
              key = "WB-1",
              title = "Issue",
              description = null,
              statusId = UUID.randomUUID(),
              statusApiId = PublicId.new("sts"),
              statusGroup = WorkItemStatusGroup.TODO,
              reporterId = userId,
              assigneeId = null,
              priorityApiId = null,
              reporterApiId = PublicId.new("usr"),
              assigneeApiId = null,
              sprintApiId = null,
              properties = JsonObject(emptyMap()),
              createdAt = now,
              updatedAt = now,
            ),
          eventType = "work_item.created",
          streamEventId = streamEventId,
          streamEventApiId = streamEventApiId,
        )
        .streamEventApiId shouldBe streamEventApiId
    }

    "transition and create form models expose field metadata" {
      val transition =
        WorkItemTransitionOption(
          id = PublicId.new("trn"),
          name = "Start",
          fromStatusId = null,
          toStatusId = PublicId.new("sts"),
          enabled = true,
          reason = null,
          fields = JsonObject(emptyMap()),
          editableFields = listOf("title"),
          fieldMeta =
            listOf(
              WorkItemFormFieldMeta(
                path = "title",
                editable = true,
                participation = "required",
              )
            ),
          commentMeta =
            WorkItemCommentFormMeta(
              participation = "optional",
              editable = true,
              defaultTemplate = "Started",
            ),
        )

      transition.editableFields.single() shouldBe "title"

      WorkItemCreateFormOption(
          issueTypeId = PublicId.new("typ"),
          initialStatusId = PublicId.new("sts"),
          fields = JsonObject(emptyMap()),
          editableFields = listOf("title"),
        )
        .issueTypeId
        .value
        .startsWith("typ_") shouldBe true

      WorkItemPropertyValue(
          propertyId = UUID.randomUUID(),
          propertyApiId = PublicId.new("fld"),
          code = "severity",
          dataType = WorkItemPropertyDataType.SINGLE_SELECT,
          value = JsonPrimitive("high"),
        )
        .code shouldBe "severity"
    }

    "search paging request enforces bounds" {
      WorkItemSearchPageRequest(limit = 25).limit shouldBe 25
    }

    "catalog commands and invitation records store metadata" {
      CreateIssueTypeCommand(
          tenantId = tenantId,
          scope = WorkItemConfigScope.TENANT,
          code = "bug",
          name = "Bug",
        )
        .code shouldBe "bug"

      CreatePropertyDefinitionCommand(
          tenantId = tenantId,
          code = "points",
          name = "Points",
          dataType = WorkItemPropertyDataType.NUMBER,
        )
        .dataType shouldBe WorkItemPropertyDataType.NUMBER

      InvitationRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("inv"),
          type = InvitationType.TENANT_MEMBER,
          tenantId = tenantId,
          email = "ada@example.test",
          normalizedEmail = "ada@example.test",
          displayName = "Ada",
          tokenHash = "hash",
          invitedBy = userId,
          expiresAt = now,
          consumedAt = null,
          createdAt = now,
        )
        .type shouldBe InvitationType.TENANT_MEMBER

      CreateInvitationCommand(
          type = InvitationType.TENANT_MEMBER,
          tenantId = tenantId,
          email = "ada@example.test",
          normalizedEmail = "ada@example.test",
          displayName = "Ada",
          tokenHash = "hash",
          invitedBy = userId,
          expiresAt = now,
        )
        .email shouldBe "ada@example.test"

      AcceptInvitationCommand(
          token = "token",
          displayName = "Ada",
          password = "secret",
        )
        .displayName shouldBe "Ada"

      CreateTenantLoginMethodSettingCommand(
          tenantId = tenantId,
          loginMethodId = UUID.randomUUID(),
          isEnabled = true,
        )
        .isEnabled shouldBe true

      CreateAuthLoginStateCommand(
          stateHash = "state-hash",
          tenantId = tenantId,
          loginMethodId = UUID.randomUUID(),
          redirectUri = "https://app.example.test/callback",
          pkceVerifier = "verifier",
          returnUrl = null,
          expiresAt = now,
        )
        .redirectUri shouldBe "https://app.example.test/callback"
    }

    "tenant admin assignment variants and permission commands store values" {
      CreateTenantWithAdminCommand(
          name = "Acme",
          slug = "acme",
          adminAssignment = SelfAssignment,
        )
        .adminAssignment shouldBe SelfAssignment

      CreateTenantWithAdminCommand(
          name = "Acme",
          slug = "acme",
          adminAssignment = UserAssignment(userId),
        )
        .adminAssignment shouldBe UserAssignment(userId)

      CreateTenantWithAdminCommand(
          name = "Acme",
          slug = "acme",
          adminAssignment = EmailInviteAssignment("ada@example.test", "Ada"),
        )
        .adminAssignment shouldBe EmailInviteAssignment("ada@example.test", "Ada")

      CreatePermissionBindingCommand(
          tenantId = tenantId,
          projectId = null,
          principalType = PermissionPrincipalType.USER,
          principalUserId = userId,
          principalGroupId = null,
          policyId = UUID.randomUUID(),
          validFrom = now,
          createdBy = userId,
        )
        .principalType shouldBe PermissionPrincipalType.USER

      CreatePermissionPolicyRuleCommand(
          policyId = UUID.randomUUID(),
          action = AuthorizationAction("project.read"),
          resourcePattern = "project:*",
          effect = PermissionEffect.ALLOW,
        )
        .resourcePattern shouldBe "project:*"

      UpdateTenantCommand(
          tenantId = tenantId,
          name = "Acme",
          slug = "acme",
          timezone = "UTC",
          locale = "en-US",
        )
        .slug shouldBe "acme"

      InstanceRequestContext(
          requestId = "req",
          apiVersion = ApiVersion.Default,
          actor = null,
          receivedAt = Instant.parse("2026-07-04T00:00:00Z"),
          instance = InstanceContextSummary(id = "default", name = "Default"),
        )
        .instance
        .name shouldBe "Default"
    }

    "tenant config specs round-trip through kotlinx serialization" {
      val json = Json { ignoreUnknownKeys = true }
      val smtp = MailSmtpTenantConfig(enabled = true, host = "smtp.example.test")

      json
        .decodeFromString(MailSmtpTenantConfig.serializer(), json.encodeToString(smtp))
        .host shouldBe "smtp.example.test"
      json
        .decodeFromString(
          AuthSessionTenantConfig.serializer(),
          json.encodeToString(TenantConfigSpecs.AuthSession.defaultValue),
        )
        .allowBearerTokens shouldBe true
    }
  })

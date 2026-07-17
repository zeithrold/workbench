package one.ztd.workbench.application.permission

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import one.ztd.workbench.identity.TenantMemberRepository
import one.ztd.workbench.identity.model.CredentialType
import one.ztd.workbench.identity.model.TenantMemberRecord
import one.ztd.workbench.identity.model.TenantMemberStatus
import one.ztd.workbench.identity.permission.AccessGrantRecord
import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import one.ztd.workbench.identity.permission.GrantScope
import one.ztd.workbench.identity.permission.PermissionBindingRepository
import one.ztd.workbench.identity.permission.ResolvedPermissionRule
import one.ztd.workbench.identity.permission.model.AuthorizationAction
import one.ztd.workbench.identity.permission.model.AuthorizationDecision
import one.ztd.workbench.identity.permission.model.AuthorizationEnvironment
import one.ztd.workbench.identity.permission.model.AuthorizationRequest
import one.ztd.workbench.identity.permission.model.AuthorizationResource
import one.ztd.workbench.identity.permission.model.AuthorizationScope
import one.ztd.workbench.identity.permission.model.AuthorizationSubject
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.kernel.common.ids.PublicId

class ScopePermissionServiceTest :
  FunSpec({
    val tenantId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    val projectId = UUID.randomUUID()
    val now = OffsetDateTime.ofInstant(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC)
    val clock = Clock.fixed(now.toInstant(), ZoneOffset.UTC)

    val tenantMembers = mockk<TenantMemberRepository>()
    val adminUserQueries = mockk<AdminUserQueryRepository>()
    val accessGrants = mockk<AccessGrantRepository>()
    val permissionBindings = mockk<PermissionBindingRepository>()

    val service =
      ScopePermissionService(
        adminUserQueries = adminUserQueries,
        accessGrants = accessGrants,
        permissionBindings = permissionBindings,
        tenantMembers = tenantMembers,
        clock = clock,
      )

    val userApiId = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0"

    fun request(scope: AuthorizationScope, project: UUID? = null) =
      AuthorizationRequest(
        scope = scope,
        subject =
          AuthorizationSubject(
            userId = userId,
            userApiId = userApiId,
            loginAccountId = null,
            credentialType = CredentialType.SESSION,
            credentialId = null,
            credentialTenantId = tenantId,
            credentialScopes = emptySet(),
          ),
        tenantId = if (scope == AuthorizationScope.TENANT) tenantId else null,
        action = AuthorizationAction("project.read"),
        resource =
          AuthorizationResource(
            type = "project",
            id = "prj_01",
            tenantId = tenantId,
            projectId = project,
          ),
        environment = AuthorizationEnvironment(requestId = "req", occurredAt = now.toInstant()),
      )

    test("instance scope allows matching grant for active instance admin") {
      coEvery { adminUserQueries.isActiveInstanceAdmin(userId, now) } returns true
      coEvery {
        accessGrants.listActiveForSubject(userId, GrantScope.INSTANCE, null, null, now)
      } returns
        listOf(
          grant(
            scope = GrantScope.INSTANCE,
            action = "project.read",
            pattern = "project:*",
            effect = PermissionEffect.ALLOW,
          )
        )

      val decision = service.decide(request(AuthorizationScope.INSTANCE))
      decision.shouldBeInstanceOf<AuthorizationDecision.Allow>()
    }

    test("batch instance decisions preserve order and reuse grant queries") {
      coEvery { adminUserQueries.isActiveInstanceAdmin(userId, now) } returns true
      coEvery {
        accessGrants.listActiveForSubject(userId, GrantScope.INSTANCE, null, null, now)
      } returns
        listOf(
          grant(
            scope = GrantScope.INSTANCE,
            action = "project.read",
            pattern = "project:*",
            effect = PermissionEffect.ALLOW,
          )
        )
      val requests =
        listOf(
          request(AuthorizationScope.INSTANCE),
          request(AuthorizationScope.INSTANCE).copy(action = AuthorizationAction("project.update")),
        )
      clearMocks(adminUserQueries, accessGrants, answers = false, recordedCalls = true)

      val decisions = service.decideAll(requests)

      decisions[0].shouldBeInstanceOf<AuthorizationDecision.Allow>()
      decisions[1].shouldBeInstanceOf<AuthorizationDecision.Deny>()
      coVerify(exactly = 1) { adminUserQueries.isActiveInstanceAdmin(userId, now) }
      coVerify(exactly = 1) {
        accessGrants.listActiveForSubject(userId, GrantScope.INSTANCE, null, null, now)
      }
    }

    test("instance scope denies without active instance admin") {
      coEvery { adminUserQueries.isActiveInstanceAdmin(userId, now) } returns false

      val decision = service.decide(request(AuthorizationScope.INSTANCE))
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
    }

    test("tenant scope denies without membership") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns null

      val decision = service.decide(request(AuthorizationScope.TENANT))
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "missing_membership"
    }

    test("tenant scope allows matching grant for active member") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, projectId, now)
      } returns
        listOf(
          rule(
            action = "project.read",
            pattern = "project:*",
            effect = PermissionEffect.ALLOW,
          )
        )

      val decision = service.decide(request(AuthorizationScope.TENANT, projectId))
      decision.shouldBeInstanceOf<AuthorizationDecision.Allow>()
    }

    test("batch tenant decisions reuse membership and binding queries") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, projectId, now)
      } returns
        listOf(
          rule(
            action = "project.read",
            pattern = "project:*",
            effect = PermissionEffect.ALLOW,
          )
        )
      val requests =
        listOf(
          request(AuthorizationScope.TENANT, projectId),
          request(AuthorizationScope.TENANT, projectId)
            .copy(action = AuthorizationAction("project.update")),
        )
      clearMocks(tenantMembers, permissionBindings, answers = false, recordedCalls = true)

      val decisions = service.decideAll(requests)

      decisions[0].shouldBeInstanceOf<AuthorizationDecision.Allow>()
      decisions[1].shouldBeInstanceOf<AuthorizationDecision.Deny>()
      coVerify(exactly = 1) { tenantMembers.findByTenantAndUser(tenantId, userId) }
      coVerify(exactly = 1) {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, projectId, now)
      }
    }

    test("instance scope denies matching deny grant") {
      coEvery { adminUserQueries.isActiveInstanceAdmin(userId, now) } returns true
      coEvery {
        accessGrants.listActiveForSubject(userId, GrantScope.INSTANCE, null, null, now)
      } returns
        listOf(
          grant(
            scope = GrantScope.INSTANCE,
            action = "project.read",
            pattern = "project:*",
            effect = PermissionEffect.DENY,
          )
        )

      val decision = service.decide(request(AuthorizationScope.INSTANCE))
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "grant_denied"
    }

    test("instance scope denies when no matching grant") {
      coEvery { adminUserQueries.isActiveInstanceAdmin(userId, now) } returns true
      coEvery {
        accessGrants.listActiveForSubject(userId, GrantScope.INSTANCE, null, null, now)
      } returns emptyList()

      val decision = service.decide(request(AuthorizationScope.INSTANCE))
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "no_matching_grant"
    }

    test("instance scope denies bearer token without matching scope") {
      coEvery { adminUserQueries.isActiveInstanceAdmin(userId, now) } returns true
      coEvery {
        accessGrants.listActiveForSubject(userId, GrantScope.INSTANCE, null, null, now)
      } returns
        listOf(
          grant(
            scope = GrantScope.INSTANCE,
            action = "project.read",
            pattern = "project:*",
            effect = PermissionEffect.ALLOW,
          )
        )

      val bearerRequest =
        request(AuthorizationScope.INSTANCE)
          .copy(
            subject =
              AuthorizationSubject(
                userId = userId,
                userApiId = userApiId,
                loginAccountId = null,
                credentialType = CredentialType.BEARER_TOKEN,
                credentialId = "token-id",
                credentialTenantId = tenantId,
                credentialScopes = setOf("other.scope"),
              )
          )

      val decision = service.decide(bearerRequest)
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "token_scope_denied"
    }

    test("tenant scope denies without tenant id") {
      val decision = service.decide(request(AuthorizationScope.TENANT).copy(tenantId = null))
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "missing_tenant"
    }

    test("tenant scope denies inactive membership") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.INVITED)

      val decision = service.decide(request(AuthorizationScope.TENANT))
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "inactive_membership"
    }

    test("tenant scope denies matching deny binding") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, null, now)
      } returns
        listOf(
          rule(
            action = "project.read",
            pattern = "project:*",
            effect = PermissionEffect.DENY,
          )
        )

      val decision = service.decide(request(AuthorizationScope.TENANT))
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "binding_denied"
    }

    test("tenant scope allows bearer token with workbench api scope") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, null, now)
      } returns
        listOf(
          rule(
            action = "project.read",
            pattern = "project:prj_01",
            effect = PermissionEffect.ALLOW,
          )
        )

      val bearerRequest =
        request(AuthorizationScope.TENANT)
          .copy(
            subject =
              AuthorizationSubject(
                userId = userId,
                userApiId = userApiId,
                loginAccountId = null,
                credentialType = CredentialType.BEARER_TOKEN,
                credentialId = "token-id",
                credentialTenantId = tenantId,
                credentialScopes = setOf("workbench.api"),
              ),
            resource =
              AuthorizationResource(
                type = "project",
                id = "prj_01",
                tenantId = tenantId,
                projectId = null,
              ),
          )

      val decision = service.decide(bearerRequest)
      decision.shouldBeInstanceOf<AuthorizationDecision.Allow>()
    }

    test("tenant scope allows bearer token with wildcard action scope") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, null, now)
      } returns
        listOf(
          rule(
            action = "project.read",
            pattern = "*",
            effect = PermissionEffect.ALLOW,
          )
        )

      val bearerRequest =
        request(AuthorizationScope.TENANT)
          .copy(
            subject =
              AuthorizationSubject(
                userId = userId,
                userApiId = userApiId,
                loginAccountId = null,
                credentialType = CredentialType.BEARER_TOKEN,
                credentialId = "token-id",
                credentialTenantId = tenantId,
                credentialScopes = setOf("project.*"),
              )
          )

      val decision = service.decide(bearerRequest)
      decision.shouldBeInstanceOf<AuthorizationDecision.Allow>()
    }

    test("tenant scope allows when allow condition matches") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, projectId, now)
      } returns
        listOf(
          rule(
            action = "issue.update",
            pattern = "issue:*",
            effect = PermissionEffect.ALLOW,
            conditionJson =
              """
              {"field":"issue.assignee","op":"eq","value":{"var":"user.currentUser"}}
              """
                .trimIndent(),
          )
        )

      val issueRequest =
        request(AuthorizationScope.TENANT, projectId)
          .copy(
            action = AuthorizationAction("issue.update"),
            resource =
              AuthorizationResource(
                type = "issue",
                id = "iss_01",
                tenantId = tenantId,
                projectId = projectId,
                attributes = mapOf("assignee" to userApiId),
              ),
          )

      val decision = service.decide(issueRequest)
      decision.shouldBeInstanceOf<AuthorizationDecision.Allow>()
    }

    test("tenant scope denies allow rule when condition does not match") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, projectId, now)
      } returns
        listOf(
          rule(
            action = "issue.update",
            pattern = "issue:*",
            effect = PermissionEffect.ALLOW,
            conditionJson =
              """
              {"field":"issue.assignee","op":"eq","value":{"var":"user.currentUser"}}
              """
                .trimIndent(),
          )
        )

      val issueRequest =
        request(AuthorizationScope.TENANT, projectId)
          .copy(
            action = AuthorizationAction("issue.update"),
            resource =
              AuthorizationResource(
                type = "issue",
                id = "iss_01",
                tenantId = tenantId,
                projectId = projectId,
                attributes = mapOf("assignee" to "usr_01JOTHERUSERABCDEFGHJKMNPQRST"),
              ),
          )

      val decision = service.decide(issueRequest)
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "no_matching_binding"
    }

    test("tenant scope denies when deny condition matches") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, projectId, now)
      } returns
        listOf(
          rule(
            action = "issue.update",
            pattern = "issue:*",
            effect = PermissionEffect.DENY,
            conditionJson =
              """
              {"field":"issue.statusGroup","op":"eq","value":"done"}
              """
                .trimIndent(),
          )
        )

      val issueRequest =
        request(AuthorizationScope.TENANT, projectId)
          .copy(
            action = AuthorizationAction("issue.update"),
            resource =
              AuthorizationResource(
                type = "issue",
                id = "iss_01",
                tenantId = tenantId,
                projectId = projectId,
                attributes = mapOf("statusGroup" to "done"),
              ),
          )

      val decision = service.decide(issueRequest)
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "binding_denied"
    }

    test("tenant scope does not deny when deny condition does not match") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, projectId, now)
      } returns
        listOf(
          rule(
            action = "issue.update",
            pattern = "issue:*",
            effect = PermissionEffect.DENY,
            conditionJson =
              """
              {"field":"issue.statusGroup","op":"eq","value":"done"}
              """
                .trimIndent(),
          )
        )

      val issueRequest =
        request(AuthorizationScope.TENANT, projectId)
          .copy(
            action = AuthorizationAction("issue.update"),
            resource =
              AuthorizationResource(
                type = "issue",
                id = "iss_01",
                tenantId = tenantId,
                projectId = projectId,
                attributes = mapOf("statusGroup" to "todo"),
              ),
          )

      val decision = service.decide(issueRequest)
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "no_matching_binding"
    }

    test("tenant scope denies with invalid deny condition json") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, projectId, now)
      } returns
        listOf(
          rule(
            action = "issue.update",
            pattern = "issue:*",
            effect = PermissionEffect.DENY,
            conditionJson = "{invalid",
          )
        )

      val issueRequest =
        request(AuthorizationScope.TENANT, projectId)
          .copy(
            action = AuthorizationAction("issue.update"),
            resource =
              AuthorizationResource(
                type = "issue",
                id = "iss_01",
                tenantId = tenantId,
                projectId = projectId,
              ),
          )

      val decision = service.decide(issueRequest)
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "binding_condition_invalid"
    }

    test("tenant scope denies allow rule with empty object condition") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, projectId, now)
      } returns
        listOf(
          rule(
            action = "issue.update",
            pattern = "issue:*",
            effect = PermissionEffect.ALLOW,
            conditionJson = "{}",
          )
        )

      val issueRequest =
        request(AuthorizationScope.TENANT, projectId)
          .copy(
            action = AuthorizationAction("issue.update"),
            resource =
              AuthorizationResource(
                type = "issue",
                id = "iss_01",
                tenantId = tenantId,
                projectId = projectId,
              ),
          )

      val decision = service.decide(issueRequest)
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "no_matching_binding"
    }

    test("tenant scope denies deny rule with empty object condition") {
      coEvery { tenantMembers.findByTenantAndUser(tenantId, userId) } returns
        membership(TenantMemberStatus.ACTIVE)
      coEvery {
        permissionBindings.listActiveRulesForSubject(userId, tenantId, projectId, now)
      } returns
        listOf(
          rule(
            action = "issue.update",
            pattern = "issue:*",
            effect = PermissionEffect.DENY,
            conditionJson = "{}",
          )
        )

      val issueRequest =
        request(AuthorizationScope.TENANT, projectId)
          .copy(
            action = AuthorizationAction("issue.update"),
            resource =
              AuthorizationResource(
                type = "issue",
                id = "iss_01",
                tenantId = tenantId,
                projectId = projectId,
              ),
          )

      val decision = service.decide(issueRequest)
      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "binding_condition_invalid"
    }
  })

private fun membership(status: TenantMemberStatus) =
  TenantMemberRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("tmb"),
    tenantId = UUID.randomUUID(),
    userId = UUID.randomUUID(),
    status = status,
    joinedAt = null,
    invitedBy = null,
    createdAt = OffsetDateTime.now(ZoneOffset.UTC),
    updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
  )

private fun grant(
  scope: GrantScope,
  tenantId: UUID? = null,
  action: String,
  pattern: String,
  effect: PermissionEffect,
): AccessGrantRecord =
  AccessGrantRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("agr"),
    scope = scope,
    tenantId = tenantId,
    projectId = null,
    subjectUserId = UUID.randomUUID(),
    action = AuthorizationAction(action),
    resourcePattern = pattern,
    effect = effect,
    validFrom = OffsetDateTime.now(ZoneOffset.UTC),
    validTo = null,
    grantedBy = null,
    createdAt = OffsetDateTime.now(ZoneOffset.UTC),
  )

private fun rule(
  action: String,
  pattern: String,
  effect: PermissionEffect,
  conditionJson: String? = null,
): ResolvedPermissionRule =
  ResolvedPermissionRule(
    bindingId = UUID.randomUUID(),
    action = AuthorizationAction(action),
    resourcePattern = pattern,
    effect = effect,
    conditionJson = conditionJson,
  )

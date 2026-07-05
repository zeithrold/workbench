package ink.doa.workbench.security.permission

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.TenantMemberRepository
import ink.doa.workbench.core.identity.model.CredentialType
import ink.doa.workbench.core.identity.model.TenantMemberRecord
import ink.doa.workbench.core.identity.model.TenantMemberStatus
import ink.doa.workbench.core.permission.AccessGrantRecord
import ink.doa.workbench.core.permission.AccessGrantRepository
import ink.doa.workbench.core.permission.AdminUserQueryRepository
import ink.doa.workbench.core.permission.GrantScope
import ink.doa.workbench.core.permission.PermissionBindingRepository
import ink.doa.workbench.core.permission.ResolvedPermissionRule
import ink.doa.workbench.core.permission.model.AuthorizationAction
import ink.doa.workbench.core.permission.model.AuthorizationDecision
import ink.doa.workbench.core.permission.model.AuthorizationEnvironment
import ink.doa.workbench.core.permission.model.AuthorizationRequest
import ink.doa.workbench.core.permission.model.AuthorizationResource
import ink.doa.workbench.core.permission.model.AuthorizationScope
import ink.doa.workbench.core.permission.model.AuthorizationSubject
import ink.doa.workbench.core.permission.model.PermissionEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

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

    fun request(scope: AuthorizationScope, project: UUID? = null) =
      AuthorizationRequest(
        scope = scope,
        subject =
          AuthorizationSubject(
            userId = userId,
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
              {"field":"assignee","op":"eq","value":{"var":"user.currentUser"}}
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
                attributes = mapOf("assignee" to userId.toString()),
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
              {"field":"assignee","op":"eq","value":{"var":"user.currentUser"}}
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
                attributes = mapOf("assignee" to UUID.randomUUID().toString()),
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
              {"field":"statusGroup","op":"eq","value":"done"}
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
              {"field":"statusGroup","op":"eq","value":"done"}
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

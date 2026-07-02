package doa.ink.workbench.service.permission

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.model.CreateTenantMemberCommand
import doa.ink.workbench.core.identity.model.CredentialType
import doa.ink.workbench.core.identity.model.TenantMemberRecord
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.permission.AssignRoleCommand
import doa.ink.workbench.core.permission.PermissionPolicyRecord
import doa.ink.workbench.core.permission.PermissionPolicyRepository
import doa.ink.workbench.core.permission.RoleAssignmentRecord
import doa.ink.workbench.core.permission.RoleAssignmentRepository
import doa.ink.workbench.core.permission.model.AuthorizationAction
import doa.ink.workbench.core.permission.model.AuthorizationDecision
import doa.ink.workbench.core.permission.model.AuthorizationEnvironment
import doa.ink.workbench.core.permission.model.AuthorizationRequest
import doa.ink.workbench.core.permission.model.AuthorizationResource
import doa.ink.workbench.core.permission.model.AuthorizationSubject
import doa.ink.workbench.core.permission.model.PermissionCondition
import doa.ink.workbench.core.permission.model.PermissionEffect
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class DefaultPermissionServiceTest :
  StringSpec({
    "allows when token scope, membership, assignment and policy all match" {
      val fixture = Fixture()

      val decision = fixture.service.decide(fixture.request())

      decision.shouldBeInstanceOf<AuthorizationDecision.Allow>()
      decision.reason.code shouldBe "policy_allowed"
    }

    "explicit deny policy wins over matching allow policy" {
      val fixture =
        Fixture(
          policies =
            listOf(
              policy(effect = PermissionEffect.ALLOW),
              policy(effect = PermissionEffect.DENY),
            )
        )

      val decision = fixture.service.decide(fixture.request())

      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "policy_denied"
    }

    "denies bearer token when scope does not allow action" {
      val fixture = Fixture()

      val decision =
        fixture.service.decide(
          fixture.request(scopes = setOf("project.read"), action = "project.create")
        )

      decision.shouldBeInstanceOf<AuthorizationDecision.Deny>()
      decision.reason.code shouldBe "token_scope_denied"
    }
  })

private class Fixture(
  private val tenantId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000201"),
  private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000202"),
  private val roleId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000203"),
  private val policies: List<PermissionPolicyRecord> = listOf(policy()),
) {
  val service =
    DefaultPermissionService(
      tenantMembers = FakeTenantMembers(tenantId, userId),
      roleAssignments = FakeRoleAssignments(tenantId, userId, roleId),
      permissionPolicies = FakePolicies(policies),
      clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
    )

  fun request(
    action: String = "project.create",
    scopes: Set<String> = setOf("workbench.api"),
  ): AuthorizationRequest =
    AuthorizationRequest(
      subject =
        AuthorizationSubject(
          userId = userId,
          loginAccountId = null,
          credentialType = CredentialType.BEARER_TOKEN,
          credentialId = "token-1",
          credentialTenantId = tenantId,
          credentialScopes = scopes,
        ),
      tenantId = tenantId,
      action = AuthorizationAction(action),
      resource =
        AuthorizationResource(
          type = "project",
          id = null,
          tenantId = tenantId,
          projectId = null,
        ),
      environment =
        AuthorizationEnvironment(
          requestId = "req-1",
          occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
        ),
    )
}

private class FakeTenantMembers(private val tenantId: UUID, private val userId: UUID) :
  TenantMemberRepository {
  override suspend fun create(command: CreateTenantMemberCommand): TenantMemberRecord =
    error("not used")

  override suspend fun findByTenantAndUser(
    tenantId: UUID,
    userId: UUID,
  ): TenantMemberRecord? =
    if (tenantId == this.tenantId && userId == this.userId) {
      TenantMemberRecord(
        id = UUID.randomUUID(),
        apiId = PublicId.new("tmb"),
        tenantId = tenantId,
        userId = userId,
        status = TenantMemberStatus.ACTIVE,
        joinedAt = OffsetDateTime.now(ZoneOffset.UTC),
        invitedBy = null,
        createdAt = OffsetDateTime.now(ZoneOffset.UTC),
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC),
      )
    } else {
      null
    }

  override suspend fun listByUser(userId: UUID): List<TenantMemberRecord> = emptyList()
}

private class FakeRoleAssignments(
  private val tenantId: UUID,
  private val userId: UUID,
  private val roleId: UUID,
) : RoleAssignmentRepository {
  override suspend fun assign(command: AssignRoleCommand): RoleAssignmentRecord = error("not used")

  override suspend fun listByTenant(tenantId: UUID): List<RoleAssignmentRecord> = emptyList()

  override suspend fun listActiveByUser(
    tenantId: UUID,
    userId: UUID,
    projectId: UUID?,
    at: OffsetDateTime,
  ): List<RoleAssignmentRecord> =
    if (tenantId == this.tenantId && userId == this.userId) {
      listOf(
        RoleAssignmentRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("ras"),
          tenantId = tenantId,
          userId = userId,
          roleId = roleId,
          projectId = projectId,
          grantedBy = null,
          validFrom = at.minusDays(1),
          validTo = null,
          createdAt = at.minusDays(1),
        )
      )
    } else {
      emptyList()
    }

  override suspend fun revoke(id: UUID, revokedAt: OffsetDateTime): Boolean = false
}

private class FakePolicies(private val policies: List<PermissionPolicyRecord>) :
  PermissionPolicyRepository {
  override suspend fun create(
    command: doa.ink.workbench.core.permission.CreatePermissionPolicyCommand
  ): PermissionPolicyRecord = error("not used")

  override suspend fun listByTenant(tenantId: UUID): List<PermissionPolicyRecord> = policies

  override suspend fun listActiveByRoles(
    tenantId: UUID,
    roleIds: Collection<UUID>,
    at: OffsetDateTime,
  ): List<PermissionPolicyRecord> = policies.filter { it.roleId in roleIds }

  override suspend fun expire(id: UUID, validTo: OffsetDateTime): Boolean = false
}

private fun policy(
  effect: PermissionEffect = PermissionEffect.ALLOW,
  action: String = "project.create",
  resourcePattern: String = "project:*",
  condition: PermissionCondition? = null,
): PermissionPolicyRecord =
  PermissionPolicyRecord(
    id = UUID.randomUUID(),
    apiId = PublicId.new("pol"),
    tenantId = UUID.fromString("00000000-0000-0000-0000-000000000201"),
    roleId = UUID.fromString("00000000-0000-0000-0000-000000000203"),
    action = AuthorizationAction(action),
    effect = effect,
    resourcePattern = resourcePattern,
    condition = condition,
    version = 1,
    validFrom = OffsetDateTime.parse("2025-01-01T00:00:00Z"),
    validTo = null,
    createdBy = null,
    createdAt = OffsetDateTime.parse("2025-01-01T00:00:00Z"),
  )

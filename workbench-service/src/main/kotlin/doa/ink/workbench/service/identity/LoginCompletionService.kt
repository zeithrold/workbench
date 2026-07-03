package doa.ink.workbench.service.identity

import doa.ink.workbench.core.common.summary.TenantSummary
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.model.AuthenticatedIdentity
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.permission.AdminUserRepository
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

enum class LoginContext {
  INSTANCE,
  TENANT,
}

data class LoginCompletion(
  val loginContext: LoginContext,
  val activeTenantId: UUID?,
  val activeTenant: TenantSummary?,
  val eligibleTenants: List<TenantSummary>,
)

@Service
class LoginCompletionService(
  private val loginAccounts: LoginAccountRepository,
  private val tenantMembers: TenantMemberRepository,
  private val tenants: TenantRepository,
  private val adminUsers: AdminUserRepository,
  private val clock: Clock,
) {
  suspend fun resolve(identity: AuthenticatedIdentity, command: LoginCommand): LoginCompletion {
    val method =
      command.loginMethodId?.let { loginAccounts.findLoginMethodByApiId(it) }
        ?: loginAccounts.findLoginMethodById(identity.loginAccount.loginMethodId)

    if (method?.code == "instance_password") {
      return LoginCompletion(
        loginContext = LoginContext.INSTANCE,
        activeTenantId = null,
        activeTenant = null,
        eligibleTenants = emptyList(),
      )
    }

    val normalized =
      command.subject?.let { doa.ink.workbench.service.identity.auth.normalizeSubject(it) }
    val tenantOptions =
      normalized?.let { loginAccounts.listLoginOptionsForIdentifier(it) } ?: emptyList()
    val methodOptions = tenantOptions.filter { option ->
      method == null || option.loginMethod.id == method.apiId.value
    }
    val activeMemberships =
      tenantMembers.listByUser(identity.user.id).filter { it.status == TenantMemberStatus.ACTIVE }
    val eligible = activeMemberships.mapNotNull { membership ->
      val tenant = tenants.findById(membership.tenantId) ?: return@mapNotNull null
      val summary = TenantSummary.from(tenant)
      if (methodOptions.any { it.tenant.id == summary.id }) summary else null
    }

    return when (eligible.size) {
      1 -> {
        val tenant = tenants.findByApiId(eligible.single().id)!!
        LoginCompletion(
          loginContext = LoginContext.TENANT,
          activeTenantId = tenant.id,
          activeTenant = eligible.single(),
          eligibleTenants = emptyList(),
        )
      }
      else ->
        LoginCompletion(
          loginContext = LoginContext.TENANT,
          activeTenantId = null,
          activeTenant = null,
          eligibleTenants = eligible,
        )
    }
  }

  suspend fun adminScopes(userId: UUID): List<String> {
    val at = OffsetDateTime.now(clock)
    val scopes = mutableListOf<String>()
    if (adminUsers.isActiveInstanceAdmin(userId, at)) {
      scopes += "instance"
    }
    adminUsers
      .listByUser(userId)
      .filter { it.scope.dbValue == "tenant" && it.validTo == null }
      .forEach {
        scopes += "tenant:${it.tenantId}"
      }
    return scopes
  }
}

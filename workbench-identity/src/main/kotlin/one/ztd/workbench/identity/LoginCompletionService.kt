package one.ztd.workbench.identity

import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import one.ztd.workbench.identity.model.AuthenticatedIdentity
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.identity.model.TenantMemberStatus
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.tenant.TenantRepository
import one.ztd.workbench.tenant.common.summary.TenantSummary
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
  val tenantDefaultLocale: String? = null,
)

@Service
class LoginCompletionService(
  private val loginMethods: LoginMethodRepository,
  private val loginDiscovery: LoginDiscoveryRepository,
  private val tenantMembers: TenantMemberRepository,
  private val tenants: TenantRepository,
  private val adminUserQueries: AdminUserQueryRepository,
  private val clock: Clock,
) {
  suspend fun resolve(identity: AuthenticatedIdentity, command: LoginCommand): LoginCompletion {
    val method =
      command.loginMethodId?.let { loginMethods.findLoginMethodByApiId(it) }
        ?: loginMethods.findLoginMethodById(identity.loginAccount.loginMethodId)

    if (method?.code == "instance_password") {
      return LoginCompletion(
        loginContext = LoginContext.INSTANCE,
        activeTenantId = null,
        activeTenant = null,
        eligibleTenants = emptyList(),
        tenantDefaultLocale = null,
      )
    }

    val normalized = command.subject?.let { one.ztd.workbench.identity.auth.normalizeSubject(it) }
    val tenantOptions =
      normalized?.let { loginDiscovery.listLoginOptionsForIdentifier(it) }.orEmpty()
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
        val tenantSummary = eligible.single()
        val tenant =
          tenants.findByApiId(tenantSummary.id.value)
            ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND)
        LoginCompletion(
          loginContext = LoginContext.TENANT,
          activeTenantId = tenant.id,
          activeTenant = tenantSummary,
          eligibleTenants = emptyList(),
          tenantDefaultLocale = tenant.locale,
        )
      }
      else ->
        LoginCompletion(
          loginContext = LoginContext.TENANT,
          activeTenantId = null,
          activeTenant = null,
          eligibleTenants = eligible,
          tenantDefaultLocale = null,
        )
    }
  }

  suspend fun adminScopes(userId: UUID): List<String> {
    val at = OffsetDateTime.now(clock)
    val scopes = mutableListOf<String>()
    if (adminUserQueries.isActiveInstanceAdmin(userId, at)) {
      scopes += "instance"
    }
    adminUserQueries
      .listByUser(userId)
      .filter { it.scope.dbValue == "tenant" && it.validTo == null }
      .forEach {
        scopes += "tenant:${it.tenantId}"
      }
    return scopes
  }
}

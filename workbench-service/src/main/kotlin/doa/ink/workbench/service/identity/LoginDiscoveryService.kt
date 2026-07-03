package doa.ink.workbench.service.identity

import doa.ink.workbench.core.common.summary.LoginMethodSummary
import doa.ink.workbench.core.common.summary.TenantSummary
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.TenantMemberRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.model.TenantLoginOption
import doa.ink.workbench.core.identity.model.TenantMemberStatus
import doa.ink.workbench.core.permission.AdminUserRepository
import doa.ink.workbench.service.identity.auth.normalizeSubject
import java.time.Clock
import java.time.OffsetDateTime
import org.springframework.stereotype.Service

enum class LoginFlow {
  INSTANCE_ONLY,
  TENANT,
}

data class LoginMethodChoiceView(
  val loginMethod: LoginMethodSummary,
  val supportedTenants: List<TenantSummary>,
)

data class LoginDiscoveryView(
  val identifierRecognized: Boolean,
  val flow: LoginFlow,
  val instancePasswordMethod: LoginMethodSummary?,
  val tenantMethods: List<LoginMethodChoiceView>,
)

@Service
class LoginDiscoveryService(
  private val users: UserRepository,
  private val loginAccounts: LoginAccountRepository,
  private val tenantMembers: TenantMemberRepository,
  private val adminUsers: AdminUserRepository,
  private val clock: Clock,
) {
  @Suppress("ReturnCount")
  suspend fun discover(identifier: String): LoginDiscoveryView {
    val normalized = normalizeSubject(identifier)
    val user =
      users.findByPrimaryEmail(normalized)
        ?: loginAccounts.findUserByMethodAndSubject("password", normalized)
        ?: loginAccounts.findUserByMethodAndSubject("instance_password", normalized)

    if (user == null) {
      return LoginDiscoveryView(
        identifierRecognized = false,
        flow = LoginFlow.TENANT,
        instancePasswordMethod = null,
        tenantMethods = emptyList(),
      )
    }

    val at = OffsetDateTime.now(clock)
    val activeMemberships =
      tenantMembers.listByUser(user.id).filter { it.status == TenantMemberStatus.ACTIVE }
    val isInstanceAdmin = adminUsers.isActiveInstanceAdmin(user.id, at)

    if (activeMemberships.isEmpty() && isInstanceAdmin) {
      val instanceMethod =
        loginAccounts.findLoginMethodByCode("instance_password")
          ?: return LoginDiscoveryView(
            identifierRecognized = true,
            flow = LoginFlow.INSTANCE_ONLY,
            instancePasswordMethod = null,
            tenantMethods = emptyList(),
          )
      return LoginDiscoveryView(
        identifierRecognized = true,
        flow = LoginFlow.INSTANCE_ONLY,
        instancePasswordMethod = LoginMethodSummary.from(instanceMethod),
        tenantMethods = emptyList(),
      )
    }

    val tenantOptions = loginAccounts.listLoginOptionsForIdentifier(normalized)
    return LoginDiscoveryView(
      identifierRecognized = true,
      flow = LoginFlow.TENANT,
      instancePasswordMethod = null,
      tenantMethods = groupTenantMethods(tenantOptions),
    )
  }

  private fun groupTenantMethods(options: List<TenantLoginOption>): List<LoginMethodChoiceView> =
    options
      .groupBy { it.loginMethod.id }
      .values
      .map { group ->
        LoginMethodChoiceView(
          loginMethod = group.first().loginMethod,
          supportedTenants = group.map { it.tenant }.distinctBy { it.id },
        )
      }
}

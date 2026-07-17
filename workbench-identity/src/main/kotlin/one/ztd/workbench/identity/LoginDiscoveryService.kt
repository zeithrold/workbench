package one.ztd.workbench.identity

import java.time.Clock
import java.time.OffsetDateTime
import one.ztd.workbench.identity.auth.normalizeSubject
import one.ztd.workbench.identity.common.summary.LoginMethodSummary
import one.ztd.workbench.identity.model.TenantLoginOption
import one.ztd.workbench.identity.model.TenantMemberStatus
import one.ztd.workbench.identity.permission.AdminUserQueryRepository
import one.ztd.workbench.tenant.common.summary.TenantSummary
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
  private val loginDiscovery: LoginDiscoveryRepository,
  private val loginMethods: LoginMethodRepository,
  private val tenantMembers: TenantMemberRepository,
  private val adminUserQueries: AdminUserQueryRepository,
  private val clock: Clock,
) {
  suspend fun discover(identifier: String): LoginDiscoveryView {
    val normalized = normalizeSubject(identifier)
    val user =
      users.findByPrimaryEmail(normalized)
        ?: loginDiscovery.findUserByMethodAndSubject("password", normalized)
        ?: loginDiscovery.findUserByMethodAndSubject("instance_password", normalized)

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
    val isInstanceAdmin = adminUserQueries.isActiveInstanceAdmin(user.id, at)

    if (activeMemberships.isEmpty() && isInstanceAdmin) {
      val instanceMethod =
        loginMethods.findLoginMethodByCode("instance_password")
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

    val tenantOptions = loginDiscovery.listLoginOptionsForIdentifier(normalized)
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

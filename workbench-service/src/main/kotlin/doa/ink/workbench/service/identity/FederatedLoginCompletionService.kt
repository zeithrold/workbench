package doa.ink.workbench.service.identity

import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.model.AuthenticationResult
import doa.ink.workbench.service.identity.auth.AuthenticationService
import doa.ink.workbench.service.identity.auth.FederatedLoginResult
import org.springframework.stereotype.Service

@Service
class FederatedLoginCompletionService(
  private val authenticationService: AuthenticationService,
  private val sessionService: SessionService,
) {
  suspend fun complete(federated: FederatedLoginResult, client: ClientContext): LoginView {
    val tenantId = federated.tenantId
    val result =
      authenticationService.completeLogin(
        identity = federated.identity,
        issueBearerToken = false,
        ipAddress = client.ipAddress,
        userAgent = client.userAgent,
        tenantIdForAudit = tenantId,
        activeTenantId = tenantId,
      )
    val completion =
      LoginCompletion(
        loginContext = LoginContext.TENANT,
        activeTenantId = tenantId,
        activeTenant = sessionService.tenantSummary(tenantId),
        eligibleTenants = emptyList(),
      )
    return toLoginView(result, completion)
  }

  private fun toLoginView(result: AuthenticationResult, completion: LoginCompletion): LoginView =
    LoginView(
      user = UserSummary.from(result.principal.user),
      sessionExpiresAt = result.session.expiresAt,
      sessionSecret = result.session.secret,
      bearerToken =
        result.bearerToken?.let {
          IssuedTokenView(
            id = it.apiId!!.value,
            token = it.secret,
            expiresAt = it.expiresAt,
          )
        },
      loginContext = completion.loginContext,
      activeTenant = completion.activeTenant,
      eligibleTenants = completion.eligibleTenants,
    )
}

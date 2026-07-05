package ink.doa.workbench.security.identity

import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.model.AuthenticationResult
import ink.doa.workbench.core.identity.model.IssuedCredential
import ink.doa.workbench.security.identity.auth.AuthenticationService
import ink.doa.workbench.security.identity.auth.FederatedLoginResult
import ink.doa.workbench.security.identity.auth.LoginCompletionRequest
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
        LoginCompletionRequest(
          identity = federated.identity,
          issueBearerToken = false,
          ipAddress = client.ipAddress,
          userAgent = client.userAgent,
          tenantIdForAudit = tenantId,
          activeTenantId = tenantId,
        )
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
            id = requireBearerTokenApiId(it),
            token = it.secret,
            expiresAt = it.expiresAt,
          )
        },
      loginContext = completion.loginContext,
      activeTenant = completion.activeTenant,
      eligibleTenants = completion.eligibleTenants,
    )

  private fun requireBearerTokenApiId(token: IssuedCredential): String =
    requireNotNull(token.apiId) { "Bearer token is missing api id." }.value
}

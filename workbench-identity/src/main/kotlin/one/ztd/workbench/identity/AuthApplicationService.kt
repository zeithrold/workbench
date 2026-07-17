package one.ztd.workbench.identity

import one.ztd.workbench.identity.auth.AuthenticationService
import one.ztd.workbench.identity.auth.BearerCredentialService
import one.ztd.workbench.identity.auth.CreateManagedBearerTokenCommand
import one.ztd.workbench.identity.auth.LoginCompletionRequest
import one.ztd.workbench.identity.common.IdentityPublicIdResolver
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.IssuedCredential
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.kernel.common.errors.AuthenticationFailedException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Service

@Service
class AuthApplicationService(
  private val authenticationService: AuthenticationService,
  private val sessionService: SessionService,
  private val loginCompletionService: LoginCompletionService,
  private val bearerCredentialService: BearerCredentialService,
  private val publicIds: IdentityPublicIdResolver,
) {
  suspend fun login(command: LoginCommand): LoginView {
    val identity = authenticationService.authenticate(command)
    val completion = loginCompletionService.resolve(identity, command)
    val result =
      authenticationService.completeLogin(
        LoginCompletionRequest(
          identity = identity,
          issueBearerToken = command.issueBearerToken,
          ipAddress = command.ipAddress,
          userAgent = command.userAgent,
          tenantIdForAudit = completion.activeTenantId,
          activeTenantId = completion.activeTenantId,
        )
      )
    return toLoginView(result, completion)
  }

  suspend fun logout(client: ClientContext, sessionSecret: String?, bearerToken: String?) {
    sessionSecret?.let {
      authenticationService.logoutSession(it, client.ipAddress, client.userAgent)
    }
    bearerToken?.let {
      bearerCredentialService.revokeBearerToken(it, client.ipAddress, client.userAgent)
    }
  }

  suspend fun issueBearerToken(
    principal: AuthenticatedPrincipal,
    tenantId: String?,
    name: String?,
    scopes: List<String>,
    client: ClientContext,
  ): IssuedTokenView {
    val loginAccountId =
      principal.loginAccountId
        ?: throw AuthenticationFailedException(WorkbenchErrorCode.AUTH_AUTHENTICATION_REQUIRED)
    val resolvedTenantId =
      tenantId?.let { publicIds.resolveTenant(it).id }
        ?: sessionService.requireActiveTenantId(principal)
    val token =
      bearerCredentialService.createBearerToken(
        CreateManagedBearerTokenCommand(
          userId = principal.user.id,
          loginAccountId = loginAccountId,
          tenantId = resolvedTenantId,
          name = name,
          scopes = scopes.toSet(),
          ipAddress = client.ipAddress,
          userAgent = client.userAgent,
        )
      )
    return IssuedTokenView(
      id = requireBearerTokenApiId(token),
      token = token.secret,
      expiresAt = token.expiresAt,
    )
  }

  suspend fun revokeBearerToken(
    principal: AuthenticatedPrincipal,
    tokenPublicId: String,
    client: ClientContext,
  ) {
    bearerCredentialService.revokeBearerTokenByApiId(
      tokenApiId = tokenPublicId,
      actorUserId = principal.user.id,
      ipAddress = client.ipAddress,
      userAgent = client.userAgent,
    )
  }

  private fun toLoginView(
    result: one.ztd.workbench.identity.model.AuthenticationResult,
    completion: LoginCompletion,
  ): LoginView =
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
      localeContext =
        LocaleContextView(
          userPreference = result.principal.user.locale,
          tenantDefault = completion.tenantDefaultLocale,
        ),
    )

  private fun requireBearerTokenApiId(token: IssuedCredential): String =
    requireNotNull(token.apiId) { "Bearer token is missing api id." }.value
}

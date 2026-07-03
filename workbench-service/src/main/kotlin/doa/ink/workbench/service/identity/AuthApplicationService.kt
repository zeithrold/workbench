package doa.ink.workbench.service.identity

import doa.ink.workbench.core.common.errors.AuthenticationFailedException
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.LoginAccountRepository
import doa.ink.workbench.core.identity.model.AuthenticatedPrincipal
import doa.ink.workbench.core.identity.model.LoginCommand
import doa.ink.workbench.service.common.PublicIdResolver
import doa.ink.workbench.service.identity.auth.AuthenticationService
import doa.ink.workbench.service.identity.auth.FederatedAuthService
import doa.ink.workbench.service.identity.auth.MagicLinkAuthService
import doa.ink.workbench.service.identity.auth.normalizeSubject
import org.springframework.stereotype.Service

@Service
@Suppress("TooManyFunctions")
class AuthApplicationService(
  private val authenticationService: AuthenticationService,
  private val sessionService: SessionService,
  private val membershipService: MembershipService,
  private val loginAccounts: LoginAccountRepository,
  private val loginDiscoveryService: LoginDiscoveryService,
  private val loginCompletionService: LoginCompletionService,
  private val federatedAuthService: FederatedAuthService,
  private val magicLinkAuthService: MagicLinkAuthService,
  private val publicIds: PublicIdResolver,
) {
  suspend fun login(command: LoginCommand): LoginView {
    val identity = authenticationService.authenticate(command)
    val completion = loginCompletionService.resolve(identity, command)
    val result =
      authenticationService.completeLogin(
        identity = identity,
        issueBearerToken = command.issueBearerToken,
        ipAddress = command.ipAddress,
        userAgent = command.userAgent,
        tenantIdForAudit = completion.activeTenantId,
        activeTenantId = completion.activeTenantId,
      )
    return toLoginView(result, completion)
  }

  suspend fun discoverLogin(identifier: String): LoginDiscoveryView =
    loginDiscoveryService.discover(identifier)

  suspend fun logout(client: ClientContext, sessionSecret: String?, bearerToken: String?) {
    sessionSecret?.let {
      authenticationService.logoutSession(it, client.ipAddress, client.userAgent)
    }
    bearerToken?.let {
      authenticationService.revokeBearerToken(it, client.ipAddress, client.userAgent)
    }
  }

  suspend fun listMemberships(principal: AuthenticatedPrincipal): List<TenantMembershipView> =
    membershipService.listActiveMemberships(principal.user.id)

  suspend fun listLoginOptions(identifier: String): List<LoginOptionView> =
    loginAccounts.listLoginOptionsForIdentifier(normalizeSubject(identifier)).map {
      LoginOptionView.from(it)
    }

  suspend fun issueBearerToken(
    principal: AuthenticatedPrincipal,
    tenantId: String?,
    name: String?,
    scopes: List<String>,
    client: ClientContext,
  ): IssuedTokenView {
    val loginAccountId =
      principal.loginAccountId ?: throw AuthenticationFailedException("Authentication required.")
    val resolvedTenantId =
      tenantId?.let { publicIds.resolveTenant(it).id }
        ?: sessionService.requireActiveTenantId(principal)
    val token =
      authenticationService.createBearerToken(
        userId = principal.user.id,
        loginAccountId = loginAccountId,
        tenantId = resolvedTenantId,
        name = name,
        scopes = scopes.toSet(),
        ipAddress = client.ipAddress,
        userAgent = client.userAgent,
      )
    return IssuedTokenView(
      id = token.apiId!!.value,
      token = token.secret,
      expiresAt = token.expiresAt,
    )
  }

  suspend fun revokeBearerToken(
    principal: AuthenticatedPrincipal,
    tokenPublicId: String,
    client: ClientContext,
  ) {
    authenticationService.revokeBearerTokenByApiId(
      tokenApiId = tokenPublicId,
      actorUserId = principal.user.id,
      ipAddress = client.ipAddress,
      userAgent = client.userAgent,
    )
  }

  suspend fun beginFederatedAuthorize(
    loginMethodId: String,
    tenantId: String,
    returnUrl: String,
    redirectUri: String,
  ): FederatedAuthorizeView {
    val result =
      federatedAuthService.beginAuthorize(
        loginMethodId = loginMethodId,
        tenantId = tenantId,
        returnUrl = returnUrl,
        redirectUri = redirectUri,
      )
    return FederatedAuthorizeView(
      authorizationUrl = result.authorizationUrl,
      state = result.state,
    )
  }

  suspend fun completeOAuthLogin(
    code: String,
    state: String,
    redirectUri: String,
    client: ClientContext,
  ): LoginView {
    val federated = federatedAuthService.completeOAuthCallback(code, state, redirectUri)
    return completeFederatedLogin(federated, client)
  }

  suspend fun completeSamlLogin(
    samlResponse: String,
    relayState: String,
    client: ClientContext,
  ): LoginView {
    val federated = federatedAuthService.completeSamlAcs(samlResponse, relayState)
    return completeFederatedLogin(federated, client)
  }

  suspend fun requestMagicLink(email: String, tenantId: String, loginMethodId: String) {
    magicLinkAuthService.requestMagicLink(
      email = email,
      tenantId = tenantId,
      loginMethodId = loginMethodId,
    )
  }

  suspend fun verifyMagicLink(token: String, client: ClientContext): LoginView {
    val identity = magicLinkAuthService.resolveToken(token)
    return completeFederatedLogin(
      doa.ink.workbench.service.identity.auth.FederatedLoginResult(
        identity =
          doa.ink.workbench.core.identity.model.AuthenticatedIdentity(
            user = identity.user,
            loginAccount = identity.loginAccount,
          ),
        tenantId = identity.tenantId,
      ),
      client,
    )
  }

  private suspend fun completeFederatedLogin(
    federated: doa.ink.workbench.service.identity.auth.FederatedLoginResult,
    client: ClientContext,
  ): LoginView {
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

  private fun toLoginView(
    result: doa.ink.workbench.core.identity.model.AuthenticationResult,
    completion: LoginCompletion,
  ): LoginView =
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

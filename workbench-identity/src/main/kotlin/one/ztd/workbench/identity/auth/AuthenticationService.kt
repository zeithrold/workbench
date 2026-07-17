package one.ztd.workbench.identity.auth

import java.time.Clock
import java.time.OffsetDateTime
import one.ztd.workbench.identity.AuthEventRepository
import one.ztd.workbench.identity.LoginAccountStore
import one.ztd.workbench.identity.model.AuditEventResult
import one.ztd.workbench.identity.model.AuthEventType
import one.ztd.workbench.identity.model.AuthenticatedIdentity
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.AuthenticationResult
import one.ztd.workbench.identity.model.CreateAuthEventCommand
import one.ztd.workbench.identity.model.CredentialType
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.kernel.common.errors.AuthenticationFailedException
import org.springframework.stereotype.Service

@Service
class AuthenticationService(
  private val loginAccounts: LoginAccountStore,
  private val authEvents: AuthEventRepository,
  private val loginOrchestrator: LoginOrchestrator,
  private val sessionCredentialService: SessionCredentialService,
  private val bearerCredentialService: BearerCredentialService,
  private val clock: Clock,
) {
  suspend fun login(command: LoginCommand): AuthenticationResult {
    val identity = authenticate(command)
    return completeLogin(
      LoginCompletionRequest(
        identity = identity,
        issueBearerToken = command.issueBearerToken,
        ipAddress = command.ipAddress,
        userAgent = command.userAgent,
      )
    )
  }

  suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity =
    try {
      loginOrchestrator.authenticate(command)
    } catch (error: AuthenticationFailedException) {
      recordLoginFailure(command, error.message)
      throw error
    }

  suspend fun completeLogin(request: LoginCompletionRequest): AuthenticationResult {
    val now = now()
    val session =
      sessionCredentialService.issueSession(
        request.identity.user.id,
        request.identity.loginAccount.id,
        now,
        request.activeTenantId,
      )
    val bearerToken =
      if (request.issueBearerToken) {
        bearerCredentialService.issueBearerToken(
          IssueBearerTokenCommand(
            userId = request.identity.user.id,
            loginAccountId = request.identity.loginAccount.id,
            tenantId = request.tenantIdForAudit,
            name = null,
            scopes = setOf("workbench.api"),
            createdBy = request.identity.user.id,
            now = now,
          )
        )
      } else null
    loginAccounts.touchLastUsed(request.identity.loginAccount.id, now)
    authEvents.append(
      CreateAuthEventCommand(
        tenantId = request.tenantIdForAudit,
        userId = request.identity.user.id,
        loginAccountId = request.identity.loginAccount.id,
        loginMethodId = request.identity.loginAccount.loginMethodId,
        eventType = AuthEventType.LOGIN_SUCCESS,
        result = AuditEventResult.SUCCESS,
        ipAddress = request.ipAddress,
        userAgent = request.userAgent,
      )
    )
    return AuthenticationResult(
      principal =
        AuthenticatedPrincipal(
          user = request.identity.user,
          loginAccountId = request.identity.loginAccount.id,
          sessionId = session.id.toString(),
          bearerTokenId = bearerToken?.id?.toString(),
          credentialType = CredentialType.SESSION,
        ),
      session = session,
      bearerToken = bearerToken,
    )
  }

  suspend fun logoutSession(
    sessionSecret: String,
    ipAddress: String?,
    userAgent: String?,
  ): Boolean = sessionCredentialService.logoutSession(sessionSecret, ipAddress, userAgent)

  private suspend fun recordLoginFailure(command: LoginCommand, reason: String?) {
    authEvents.append(
      CreateAuthEventCommand(
        tenantId = null,
        eventType = AuthEventType.LOGIN_FAILURE,
        result = AuditEventResult.FAILURE,
        failureReason = reason ?: "invalid_credentials",
        ipAddress = command.ipAddress,
        userAgent = command.userAgent,
      )
    )
  }

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}

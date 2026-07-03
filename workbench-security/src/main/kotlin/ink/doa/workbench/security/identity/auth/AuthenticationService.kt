package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.AuthenticationFailedException
import ink.doa.workbench.core.identity.AuthEventRepository
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.model.AuditEventResult
import ink.doa.workbench.core.identity.model.AuthEventType
import ink.doa.workbench.core.identity.model.AuthenticatedIdentity
import ink.doa.workbench.core.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.core.identity.model.AuthenticationResult
import ink.doa.workbench.core.identity.model.CreateAuthEventCommand
import ink.doa.workbench.core.identity.model.CredentialType
import ink.doa.workbench.core.identity.model.LoginCommand
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
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
    return completeLogin(identity, command.issueBearerToken, command.ipAddress, command.userAgent)
  }

  suspend fun authenticate(command: LoginCommand): AuthenticatedIdentity =
    try {
      loginOrchestrator.authenticate(command)
    } catch (error: AuthenticationFailedException) {
      recordLoginFailure(command, error.message)
      throw error
    }

  suspend fun completeLogin(
    identity: AuthenticatedIdentity,
    issueBearerToken: Boolean,
    ipAddress: String?,
    userAgent: String?,
    tenantIdForAudit: UUID? = null,
    activeTenantId: UUID? = null,
  ): AuthenticationResult {
    val now = now()
    val session =
      sessionCredentialService.issueSession(
        identity.user.id,
        identity.loginAccount.id,
        now,
        activeTenantId,
      )
    val bearerToken =
      if (issueBearerToken) {
        bearerCredentialService.issueBearerToken(
          userId = identity.user.id,
          loginAccountId = identity.loginAccount.id,
          tenantId = tenantIdForAudit,
          name = null,
          scopes = setOf("workbench.api"),
          createdBy = identity.user.id,
          now = now,
        )
      } else null
    loginAccounts.touchLastUsed(identity.loginAccount.id, now)
    authEvents.append(
      CreateAuthEventCommand(
        tenantId = tenantIdForAudit,
        userId = identity.user.id,
        loginAccountId = identity.loginAccount.id,
        loginMethodId = identity.loginAccount.loginMethodId,
        eventType = AuthEventType.LOGIN_SUCCESS,
        result = AuditEventResult.SUCCESS,
        ipAddress = ipAddress,
        userAgent = userAgent,
      )
    )
    return AuthenticationResult(
      principal =
        AuthenticatedPrincipal(
          user = identity.user,
          loginAccountId = identity.loginAccount.id,
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

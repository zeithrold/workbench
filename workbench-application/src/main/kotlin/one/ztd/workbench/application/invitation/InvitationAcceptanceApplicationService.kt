package one.ztd.workbench.application.invitation

import one.ztd.workbench.identity.AuthApplicationService
import one.ztd.workbench.identity.ClientContext
import one.ztd.workbench.identity.SessionService
import one.ztd.workbench.identity.model.AcceptInvitationCommand
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.identity.model.LoginMethodKind
import org.springframework.stereotype.Service

data class InvitationAcceptanceWithSession(
  val acceptance: InvitationAcceptView,
  val sessionSecret: String,
  val sessionExpiresAt: java.time.OffsetDateTime,
)

@Service
class InvitationAcceptanceApplicationService(
  private val invitations: InvitationService,
  private val authentication: AuthApplicationService,
  private val sessions: SessionService,
) {
  suspend fun acceptNew(
    command: AcceptInvitationCommand,
    client: ClientContext,
  ): InvitationAcceptanceWithSession {
    val acceptance = invitations.accept(command)
    val login =
      authentication.login(
        LoginCommand(
          method = LoginMethodKind.PASSWORD,
          tenantId = acceptance.tenant.id.value,
          subject = requireNotNull(acceptance.user.primaryEmail),
          password = command.password,
          ipAddress = client.ipAddress,
          userAgent = client.userAgent,
        )
      )
    return InvitationAcceptanceWithSession(
      acceptance = acceptance,
      sessionSecret = login.sessionSecret,
      sessionExpiresAt = login.sessionExpiresAt,
    )
  }

  suspend fun acceptExisting(
    token: String,
    principal: AuthenticatedPrincipal,
  ): InvitationAcceptView {
    val acceptance = invitations.acceptExisting(token, principal.user)
    sessions.switchTenant(principal, acceptance.tenant.id.value)
    return acceptance
  }
}

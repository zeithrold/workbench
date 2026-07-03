package doa.ink.workbench.service.invitation

import doa.ink.workbench.core.common.context.RequestHost
import doa.ink.workbench.core.common.errors.InvalidRequestException
import doa.ink.workbench.core.common.errors.ResourceConflictException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.summary.TenantSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.InvitationRepository
import doa.ink.workbench.core.identity.LoginAccountStore
import doa.ink.workbench.core.identity.LoginMethodRepository
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.UserLoginAccountRepository
import doa.ink.workbench.core.identity.UserRepository
import doa.ink.workbench.core.identity.auth.CredentialHasher
import doa.ink.workbench.core.identity.auth.CredentialSecretGenerator
import doa.ink.workbench.core.identity.auth.PasswordHasher
import doa.ink.workbench.core.identity.model.AcceptInvitationCommand
import doa.ink.workbench.core.identity.model.CreateInvitationCommand
import doa.ink.workbench.core.identity.model.CreateLoginAccountCommand
import doa.ink.workbench.core.identity.model.CreateUserCommand
import doa.ink.workbench.core.identity.model.InvitationRecord
import doa.ink.workbench.core.identity.model.InvitationType
import doa.ink.workbench.core.identity.model.LinkUserLoginAccountCommand
import doa.ink.workbench.core.identity.model.LoginAccountParameterKey
import doa.ink.workbench.core.identity.model.TenantStatus
import doa.ink.workbench.core.identity.model.UpdateTenantCommand
import doa.ink.workbench.core.identity.model.UpsertLoginAccountParameterCommand
import doa.ink.workbench.core.identity.model.UserRecord
import doa.ink.workbench.service.identity.auth.normalizeSubject
import doa.ink.workbench.service.permission.AdminUserService
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.stereotype.Service

private const val PASSWORD_METHOD_CODE = "password"

@Service
class InvitationService(
  private val invitations: InvitationRepository,
  private val tenants: TenantRepository,
  private val users: UserRepository,
  private val loginMethods: LoginMethodRepository,
  private val loginAccounts: LoginAccountStore,
  private val userLoginAccounts: UserLoginAccountRepository,
  private val adminUserService: AdminUserService,
  private val credentialHasher: CredentialHasher,
  private val secretGenerator: CredentialSecretGenerator,
  private val passwordHasher: PasswordHasher,
  private val invitationLinkBuilder: InvitationLinkBuilder,
  private val clock: Clock,
) {
  private val invitationTtl = Duration.ofDays(7)

  suspend fun create(
    type: InvitationType,
    tenantId: UUID,
    email: String,
    displayName: String?,
    invitedBy: UUID,
    requestHost: RequestHost?,
  ): CreateInvitationResult {
    val normalizedEmail = normalizeSubject(email)
    val secret = secretGenerator.generate()
    val now = now()
    invitations.create(
      CreateInvitationCommand(
        type = type,
        tenantId = tenantId,
        email = email.trim(),
        normalizedEmail = normalizedEmail,
        displayName = displayName,
        tokenHash = credentialHasher.hash(secret),
        invitedBy = invitedBy,
        expiresAt = now.plus(invitationTtl),
      )
    )
    return CreateInvitationResult(
      token = secret,
      invitationLink = invitationLinkBuilder.buildInvitationLink(secret, requestHost),
    )
  }

  suspend fun preview(token: String): InvitationPreviewView {
    val invitation = requireActiveInvitation(token)
    val tenant =
      tenants.findById(invitation.tenantId) ?: throw ResourceNotFoundException("Tenant not found.")
    return InvitationPreviewView(
      type = invitation.type,
      tenant = TenantSummary.from(tenant),
      email = invitation.email,
      displayName = invitation.displayName,
    )
  }

  suspend fun accept(command: AcceptInvitationCommand): InvitationAcceptView {
    val invitation = requireActiveInvitation(command.token)
    return when (invitation.type) {
      InvitationType.TENANT_ADMIN -> acceptTenantAdmin(invitation, command)
      InvitationType.TENANT_MEMBER ->
        throw InvalidRequestException("Tenant member invitations are not supported yet.")
    }
  }

  private suspend fun acceptTenantAdmin(
    invitation: InvitationRecord,
    command: AcceptInvitationCommand,
  ): InvitationAcceptView {
    val tenant = requirePendingTenant(invitation.tenantId)
    ensureEmailAvailable(invitation.normalizedEmail)
    val user = createInvitedUser(invitation, command)
    adminUserService.provisionTenantAdmin(
      tenantId = tenant.id,
      userId = user.id,
      actorUserId = invitation.invitedBy,
    )
    val activatedTenant =
      tenants.update(
        UpdateTenantCommand(
          tenantId = tenant.id,
          status = TenantStatus.ACTIVE,
        )
      )
    invitations.consume(invitation.id, now())
    return InvitationAcceptView(
      type = InvitationType.TENANT_ADMIN,
      tenant = TenantSummary.from(activatedTenant),
      user = UserSummary.from(user),
    )
  }

  private suspend fun requirePendingTenant(tenantId: UUID) =
    tenants.findById(tenantId)?.takeIf { it.status == TenantStatus.PENDING_ACTIVATION }
      ?: throw InvalidRequestException("Tenant is not pending activation.")

  private suspend fun ensureEmailAvailable(normalizedEmail: String) {
    if (users.findByPrimaryEmail(normalizedEmail) != null) {
      throw ResourceConflictException("A user with this email already exists.")
    }
  }

  private suspend fun createInvitedUser(
    invitation: InvitationRecord,
    command: AcceptInvitationCommand,
  ): UserRecord {
    val passwordMethod =
      loginMethods.findLoginMethodByCode(PASSWORD_METHOD_CODE)
        ?: throw ResourceNotFoundException("Password login method is not configured.")
    val user =
      users.create(
        CreateUserCommand(
          displayName = command.displayName,
          primaryEmail = invitation.normalizedEmail,
        )
      )
    val loginAccount =
      loginAccounts.createLoginAccount(
        CreateLoginAccountCommand(
          loginMethodId = passwordMethod.id,
          subject = invitation.email,
          normalizedSubject = invitation.normalizedEmail,
          displayName = command.displayName,
        )
      )
    loginAccounts.upsertParameter(
      UpsertLoginAccountParameterCommand(
        loginAccountId = loginAccount.id,
        parameterKey = LoginAccountParameterKey.PasswordHash,
        parameterValue = passwordHasher.hash(command.password),
      )
    )
    userLoginAccounts.linkUser(
      LinkUserLoginAccountCommand(
        userId = user.id,
        loginAccountId = loginAccount.id,
        linkedBy = user.id,
      )
    )
    return user
  }

  private suspend fun requireActiveInvitation(token: String) =
    invitations.findActiveByHash(credentialHasher.hash(token), now())
      ?: throw InvalidRequestException("Invitation is invalid or expired.")

  private fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}

data class CreateInvitationResult(val token: String, val invitationLink: String)

data class InvitationPreviewView(
  val type: InvitationType,
  val tenant: TenantSummary,
  val email: String,
  val displayName: String?,
)

data class InvitationAcceptView(
  val type: InvitationType,
  val tenant: TenantSummary,
  val user: UserSummary,
)

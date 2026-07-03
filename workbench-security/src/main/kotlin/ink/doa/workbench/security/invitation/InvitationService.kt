package ink.doa.workbench.security.invitation

import ink.doa.workbench.core.common.context.RequestHost
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.ResourceConflictException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.core.identity.InvitationRepository
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserLoginAccountRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.auth.CredentialHasher
import ink.doa.workbench.core.identity.auth.CredentialSecretGenerator
import ink.doa.workbench.core.identity.auth.PasswordHasher
import ink.doa.workbench.core.identity.model.AcceptInvitationCommand
import ink.doa.workbench.core.identity.model.CreateInvitationCommand
import ink.doa.workbench.core.identity.model.CreateLoginAccountCommand
import ink.doa.workbench.core.identity.model.CreateUserCommand
import ink.doa.workbench.core.identity.model.InvitationRecord
import ink.doa.workbench.core.identity.model.InvitationType
import ink.doa.workbench.core.identity.model.LinkUserLoginAccountCommand
import ink.doa.workbench.core.identity.model.LoginAccountParameterKey
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UpdateTenantCommand
import ink.doa.workbench.core.identity.model.UpsertLoginAccountParameterCommand
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.security.identity.auth.normalizeSubject
import ink.doa.workbench.security.permission.AdminUserService
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
      tenants.findById(invitation.tenantId)
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND)
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
        throw InvalidRequestException(WorkbenchErrorCode.TENANT_MEMBER_INVITATION_UNSUPPORTED)
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
      ?: throw InvalidRequestException(WorkbenchErrorCode.TENANT_PENDING_ACTIVATION_REQUIRED)

  private suspend fun ensureEmailAvailable(normalizedEmail: String) {
    if (users.findByPrimaryEmail(normalizedEmail) != null) {
      throw ResourceConflictException(WorkbenchErrorCode.USER_EMAIL_ALREADY_EXISTS)
    }
  }

  private suspend fun createInvitedUser(
    invitation: InvitationRecord,
    command: AcceptInvitationCommand,
  ): UserRecord {
    val passwordMethod =
      loginMethods.findLoginMethodByCode(PASSWORD_METHOD_CODE)
        ?: throw ResourceNotFoundException(
          WorkbenchErrorCode.RESOURCE_PASSWORD_LOGIN_METHOD_NOT_FOUND
        )
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
      ?: throw InvalidRequestException(WorkbenchErrorCode.INVITATION_INVALID_OR_EXPIRED)

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

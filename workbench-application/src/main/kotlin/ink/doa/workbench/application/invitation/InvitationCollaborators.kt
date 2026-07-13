package ink.doa.workbench.application.invitation

import ink.doa.workbench.application.permission.AdminUserService
import ink.doa.workbench.identity.InvitationRepository
import ink.doa.workbench.identity.LoginAccountStore
import ink.doa.workbench.identity.LoginMethodRepository
import ink.doa.workbench.identity.TenantMemberRepository
import ink.doa.workbench.identity.UserLoginAccountRepository
import ink.doa.workbench.identity.UserRepository
import ink.doa.workbench.identity.auth.CredentialHasher
import ink.doa.workbench.identity.auth.CredentialSecretGenerator
import ink.doa.workbench.identity.auth.PasswordHasher
import ink.doa.workbench.identity.invitation.InvitationLinkBuilder
import ink.doa.workbench.tenant.TenantRepository
import org.springframework.stereotype.Component

@Component
class InvitationLoginSupport(
  val loginMethods: LoginMethodRepository,
  val loginAccounts: LoginAccountStore,
  val userLoginAccounts: UserLoginAccountRepository,
  val passwordHasher: PasswordHasher,
)

@Component
class InvitationIdentitySupport(
  val tenants: TenantRepository,
  val tenantMembers: TenantMemberRepository,
  val users: UserRepository,
  val login: InvitationLoginSupport,
)

@Component
class InvitationCollaborators(
  val invitations: InvitationRepository,
  val credentialHasher: CredentialHasher,
  val secretGenerator: CredentialSecretGenerator,
  val invitationLinkBuilder: InvitationLinkBuilder,
  val adminUserService: AdminUserService,
)

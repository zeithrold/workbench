package ink.doa.workbench.security.invitation

import ink.doa.workbench.core.identity.InvitationRepository
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.LoginMethodRepository
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.UserLoginAccountRepository
import ink.doa.workbench.core.identity.UserRepository
import ink.doa.workbench.core.identity.auth.CredentialHasher
import ink.doa.workbench.core.identity.auth.CredentialSecretGenerator
import ink.doa.workbench.core.identity.auth.PasswordHasher
import ink.doa.workbench.security.permission.AdminUserService
import org.springframework.stereotype.Component

@Component
class InvitationIdentitySupport(
  val tenants: TenantRepository,
  val users: UserRepository,
  val loginMethods: LoginMethodRepository,
  val loginAccounts: LoginAccountStore,
  val userLoginAccounts: UserLoginAccountRepository,
  val passwordHasher: PasswordHasher,
)

@Component
class InvitationCollaborators(
  val invitations: InvitationRepository,
  val credentialHasher: CredentialHasher,
  val secretGenerator: CredentialSecretGenerator,
  val invitationLinkBuilder: InvitationLinkBuilder,
  val adminUserService: AdminUserService,
)

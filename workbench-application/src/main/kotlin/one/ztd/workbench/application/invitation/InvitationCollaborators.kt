package one.ztd.workbench.application.invitation

import one.ztd.workbench.application.permission.AdminUserService
import one.ztd.workbench.identity.InvitationRepository
import one.ztd.workbench.identity.LoginAccountStore
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.TenantMemberRepository
import one.ztd.workbench.identity.UserLoginAccountRepository
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.identity.auth.CredentialHasher
import one.ztd.workbench.identity.auth.CredentialSecretGenerator
import one.ztd.workbench.identity.auth.PasswordHasher
import one.ztd.workbench.identity.invitation.InvitationLinkBuilder
import one.ztd.workbench.tenant.TenantRepository
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

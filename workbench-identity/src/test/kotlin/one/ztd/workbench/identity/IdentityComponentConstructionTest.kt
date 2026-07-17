package one.ztd.workbench.identity

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.mockk
import one.ztd.workbench.identity.auth.PasswordHasher
import one.ztd.workbench.identity.auth.SecureRandomCredentialSecretGenerator
import one.ztd.workbench.identity.auth.Sha256CredentialHasher
import one.ztd.workbench.identity.permission.AccessGrantRepository
import one.ztd.workbench.identity.permission.AdminUserCommandRepository
import one.ztd.workbench.identity.permission.AdminUserQueryRepository

class IdentityComponentConstructionTest :
  StringSpec({
    "credential components generate opaque secrets and stable hashes" {
      val secret = SecureRandomCredentialSecretGenerator().generate()
      val hasher = Sha256CredentialHasher()

      secret.isNotBlank() shouldBe true
      hasher.hash("secret") shouldBe hasher.hash("secret")
      hasher.hash("secret") shouldStartWith "sha256:"
    }

    "identity support components expose explicitly supplied ports" {
      val users = mockk<UserRepository>()
      val loginMethods = mockk<LoginMethodRepository>()
      val loginAccounts = mockk<LoginAccountStore>()
      val userLoginAccounts = mockk<UserLoginAccountRepository>()
      val passwordHasher = mockk<PasswordHasher>()
      val accountSupport =
        BootstrapAccountSupport(
          users,
          loginMethods,
          loginAccounts,
          userLoginAccounts,
          passwordHasher,
        )
      val commands = mockk<AdminUserCommandRepository>()
      val queries = mockk<AdminUserQueryRepository>()
      val grants = mockk<AccessGrantRepository>()
      val adminSupport = BootstrapAdminSupport(commands, queries, grants)

      accountSupport.users shouldBe users
      accountSupport.loginMethods shouldBe loginMethods
      accountSupport.loginAccounts shouldBe loginAccounts
      accountSupport.userLoginAccounts shouldBe userLoginAccounts
      accountSupport.passwordHasher shouldBe passwordHasher
      adminSupport.adminUserCommands shouldBe commands
      adminSupport.adminUserQueries shouldBe queries
      adminSupport.accessGrants shouldBe grants
      IdentityModuleConfiguration()::class shouldBe IdentityModuleConfiguration::class
    }
  })

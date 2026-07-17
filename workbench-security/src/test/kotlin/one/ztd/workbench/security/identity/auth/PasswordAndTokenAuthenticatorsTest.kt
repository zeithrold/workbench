package one.ztd.workbench.security.identity.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import one.ztd.workbench.identity.LoginAccountStore
import one.ztd.workbench.identity.LoginMethodRepository
import one.ztd.workbench.identity.UserLoginAccountRepository
import one.ztd.workbench.identity.auth.CredentialHasher
import one.ztd.workbench.identity.auth.PasswordVerifier
import one.ztd.workbench.identity.model.LoginAccountParameterKey
import one.ztd.workbench.identity.model.LoginAccountParameterRecord
import one.ztd.workbench.identity.model.LoginAccountRecord
import one.ztd.workbench.identity.model.LoginCommand
import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.errors.AuthenticationFailedException
import one.ztd.workbench.kernel.common.ids.PublicId

class PasswordAndTokenAuthenticatorsTest :
  StringSpec({
    val loginMethods = mockk<LoginMethodRepository>()
    val loginAccounts = mockk<LoginAccountStore>()
    val userLoginAccounts = mockk<UserLoginAccountRepository>()
    val passwordVerifier = mockk<PasswordVerifier>()
    val credentialHasher = mockk<CredentialHasher>()
    val passwordAuthenticator =
      PasswordLoginAuthenticator(loginMethods, loginAccounts, userLoginAccounts, passwordVerifier)
    val tokenAuthenticator =
      ApiTokenLoginAuthenticator(
        loginMethods,
        loginAccounts,
        userLoginAccounts,
        credentialHasher,
      )

    val accountId = UUID.randomUUID()
    val userId = UUID.randomUUID()
    val account =
      LoginAccountRecord(
        id = accountId,
        apiId = PublicId.new("lac"),
        loginMethodId = UUID.randomUUID(),
        subject = "ada@example.test",
        normalizedSubject = "ada@example.test",
        displayName = "Ada",
        lastUsedAt = null,
        disabledAt = null,
        disabledBy = null,
        createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
      )
    val user =
      UserRecord(
        id = userId,
        apiId = PublicId.new("usr"),
        displayName = "Ada",
        primaryEmail = "ada@example.test",
      )

    "password authenticator returns linked identity" {
      coEvery {
        loginAccounts.findLoginAccountByMethodAndSubject("password", "ada@example.test")
      } returns account
      coEvery {
        loginAccounts.findParameter(accountId, LoginAccountParameterKey.PasswordHash)
      } returns
        LoginAccountParameterRecord(
          id = UUID.randomUUID(),
          loginAccountId = accountId,
          parameterKey = LoginAccountParameterKey.PasswordHash,
          parameterValue = "hash",
          secretRef = null,
          metadata = JsonObject(emptyMap()),
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      every { passwordVerifier.verify("secret", "hash") } returns true
      coEvery { userLoginAccounts.findLinkedUser(accountId) } returns user

      val identity = runBlocking {
        passwordAuthenticator.authenticate(
          LoginCommand(
            method = LoginMethodKind.PASSWORD,
            subject = "ada@example.test",
            password = "secret",
          )
        )
      }

      identity.user.id shouldBe userId
      identity.loginAccount.id shouldBe accountId
    }

    "password authenticator rejects invalid password" {
      coEvery {
        loginAccounts.findLoginAccountByMethodAndSubject("password", "ada@example.test")
      } returns account
      coEvery {
        loginAccounts.findParameter(accountId, LoginAccountParameterKey.PasswordHash)
      } returns
        LoginAccountParameterRecord(
          id = UUID.randomUUID(),
          loginAccountId = accountId,
          parameterKey = LoginAccountParameterKey.PasswordHash,
          parameterValue = "hash",
          secretRef = null,
          metadata = JsonObject(emptyMap()),
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      every { passwordVerifier.verify("wrong", "hash") } returns false

      shouldThrow<AuthenticationFailedException> {
        runBlocking {
          passwordAuthenticator.authenticate(
            LoginCommand(
              method = LoginMethodKind.PASSWORD,
              subject = "ada@example.test",
              password = "wrong",
            )
          )
        }
      }
    }

    "api token authenticator resolves account by token hash" {
      val method =
        LoginMethodDefinitionRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("lmd"),
          code = "api_token",
          kind = LoginMethodKind.API_TOKEN,
          name = "API Token",
          isBuiltin = true,
          isEnabledGlobally = true,
          createdAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
          updatedAt = OffsetDateTime.parse("2026-07-04T00:00:00Z"),
        )
      coEvery { loginMethods.findLoginMethodByApiId(method.apiId.value) } returns method
      every { credentialHasher.hash("token-value") } returns "token-hash"
      coEvery {
        loginAccounts.findLoginAccountByParameterValue(
          loginMethodCode = "api_token",
          parameterKey = LoginAccountParameterKey.ApiTokenHash,
          parameterValue = "token-hash",
        )
      } returns account
      coEvery { userLoginAccounts.findLinkedUser(accountId) } returns user

      val identity = runBlocking {
        tokenAuthenticator.authenticate(
          LoginCommand(
            method = LoginMethodKind.API_TOKEN,
            loginMethodId = method.apiId.value,
            token = "token-value",
          )
        )
      }

      identity.user.id shouldBe userId
    }
  })

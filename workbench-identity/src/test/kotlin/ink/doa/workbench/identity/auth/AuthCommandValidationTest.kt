package ink.doa.workbench.identity.auth

import ink.doa.workbench.identity.model.LoginCommand
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AuthCommandValidationTest :
  StringSpec({
    val passwordCommand =
      LoginCommand(
        method = LoginMethodKind.PASSWORD,
        subject = "ada@example.test",
        password = "secret",
      )

    "requireLoginSubject returns subject" {
      requireLoginSubject(passwordCommand) shouldBe "ada@example.test"
    }

    "requireLoginSubject throws when missing" {
      shouldThrow<InvalidRequestException> {
          requireLoginSubject(passwordCommand.copy(subject = null))
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_LOGIN_SUBJECT_REQUIRED
    }

    "requireLoginPassword returns password" {
      requireLoginPassword(passwordCommand) shouldBe "secret"
    }

    "requireLoginPassword throws when missing" {
      shouldThrow<InvalidRequestException> {
          requireLoginPassword(passwordCommand.copy(password = null))
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_LOGIN_PASSWORD_REQUIRED
    }

    "requireLoginToken throws when missing" {
      shouldThrow<InvalidRequestException> {
          requireLoginToken(LoginCommand(method = LoginMethodKind.API_TOKEN))
        }
        .errorCode shouldBe WorkbenchErrorCode.IDENTITY_LOGIN_TOKEN_REQUIRED
    }

    "require ldap fields" {
      val ldap =
        LoginCommand(
          method = LoginMethodKind.LDAP,
          loginMethodId = "lmd_abc",
          tenantId = "ten_abc",
          subject = "ada",
          password = "secret",
        )
      requireLdapLoginMethodId(ldap) shouldBe "lmd_abc"
      requireLdapTenantId(ldap) shouldBe "ten_abc"
      requireLdapSubject(ldap) shouldBe "ada"
      requireLdapPassword(ldap) shouldBe "secret"
    }

    "require ldap fields throw when missing" {
      val ldap =
        LoginCommand(
          method = LoginMethodKind.LDAP,
          loginMethodId = null,
          tenantId = null,
          subject = null,
          password = null,
        )

      shouldThrow<InvalidRequestException> { requireLdapLoginMethodId(ldap) }
      shouldThrow<InvalidRequestException> { requireLdapTenantId(ldap) }
      shouldThrow<InvalidRequestException> { requireLdapSubject(ldap) }
      shouldThrow<InvalidRequestException> { requireLdapPassword(ldap) }
    }
  })

package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.model.LoginCommand
import ink.doa.workbench.core.identity.model.LoginMethodKind
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
  })

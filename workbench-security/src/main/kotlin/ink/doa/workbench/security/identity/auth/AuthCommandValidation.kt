package ink.doa.workbench.security.identity.auth

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.identity.model.LoginCommand

fun requireLoginSubject(command: LoginCommand): String =
  command.subject
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.IDENTITY_LOGIN_SUBJECT_REQUIRED,
      "subject is required for password login.",
    )

fun requireLoginPassword(command: LoginCommand): String =
  command.password
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.IDENTITY_LOGIN_PASSWORD_REQUIRED,
      "password is required for password login.",
    )

fun requireLoginToken(command: LoginCommand): String =
  command.token ?: throw InvalidRequestException(WorkbenchErrorCode.IDENTITY_LOGIN_TOKEN_REQUIRED)

fun requireLdapLoginMethodId(command: LoginCommand): String =
  command.loginMethodId
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.IDENTITY_LOGIN_METHOD_ID_REQUIRED,
      "loginMethodId is required for ldap login.",
    )

fun requireLdapTenantId(command: LoginCommand): String =
  command.tenantId
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.IDENTITY_LOGIN_TENANT_ID_REQUIRED,
      "tenantId is required for ldap login.",
    )

fun requireLdapSubject(command: LoginCommand): String =
  command.subject
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.IDENTITY_LOGIN_SUBJECT_REQUIRED,
      "subject is required for ldap login.",
    )

fun requireLdapPassword(command: LoginCommand): String =
  command.password
    ?: throw InvalidRequestException(
      WorkbenchErrorCode.IDENTITY_LOGIN_PASSWORD_REQUIRED,
      "password is required for ldap login.",
    )

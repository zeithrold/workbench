package ink.doa.workbench.data.identity

import ink.doa.workbench.core.common.summary.LoginMethodSummary
import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.identity.LoginAccountStore
import ink.doa.workbench.core.identity.LoginDiscoveryRepository
import ink.doa.workbench.core.identity.UserLoginAccountRepository
import ink.doa.workbench.core.identity.model.TenantLoginOption
import ink.doa.workbench.core.identity.model.UserRecord
import ink.doa.workbench.data.persistence.LoginMethodDefinitionsTable
import ink.doa.workbench.data.persistence.TenantLoginMethodSettingsTable
import ink.doa.workbench.data.persistence.TenantMembersTable
import ink.doa.workbench.data.persistence.TenantsTable
import ink.doa.workbench.data.persistence.UsersTable
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedLoginDiscoveryRepository(
  private val database: Database,
  private val loginAccountStore: LoginAccountStore,
  private val userLoginAccounts: UserLoginAccountRepository,
) : LoginDiscoveryRepository {
  override suspend fun listLoginOptionsForIdentifier(
    normalizedIdentifier: String
  ): List<TenantLoginOption> =
    suspendTransaction(db = database) {
      val user =
        UsersTable.selectAll()
          .where {
            (UsersTable.deletedAt.isNull()) and (UsersTable.primaryEmail eq normalizedIdentifier)
          }
          .singleOrNull()
          ?.toUserRecord() ?: findUserByMethodAndSubject("password", normalizedIdentifier)

      if (user == null) {
        return@suspendTransaction emptyList()
      }

      val memberships =
        TenantMembersTable.selectAll()
          .where {
            (TenantMembersTable.userId eq user.id.toKotlinUuid()) and
              (TenantMembersTable.status eq "active") and
              TenantMembersTable.deletedAt.isNull()
          }
          .map { it.toTenantMemberRecord() }

      memberships.flatMap { membership ->
        val tenant =
          TenantsTable.selectAll()
            .where {
              (TenantsTable.id eq membership.tenantId.toKotlinUuid()) and
                TenantsTable.deletedAt.isNull()
            }
            .singleOrNull()
            ?.toTenantRecord() ?: return@flatMap emptyList()

        TenantLoginMethodSettingsTable.selectAll()
          .where {
            (TenantLoginMethodSettingsTable.tenantId eq membership.tenantId.toKotlinUuid()) and
              TenantLoginMethodSettingsTable.isEnabled
          }
          .mapNotNull { settingRow ->
            val methodRow =
              LoginMethodDefinitionsTable.selectAll()
                .where {
                  (LoginMethodDefinitionsTable.id eq
                    settingRow[TenantLoginMethodSettingsTable.loginMethodId]) and
                    LoginMethodDefinitionsTable.isEnabledGlobally
                }
                .singleOrNull() ?: return@mapNotNull null
            val method = methodRow.toLoginMethodDefinitionRecord()
            TenantLoginOption(
              tenant = TenantSummary.from(tenant),
              loginMethod = LoginMethodSummary.from(method),
            )
          }
      }
    }

  override suspend fun findUserByMethodAndSubject(
    loginMethodCode: String,
    normalizedSubject: String,
  ): UserRecord? {
    val loginAccount =
      loginAccountStore.findLoginAccountByMethodAndSubject(loginMethodCode, normalizedSubject)
        ?: return null
    return userLoginAccounts.findLinkedUser(loginAccount.id)
  }
}

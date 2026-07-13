package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.data.persistence.postgres.identity.LoginMethodDefinitionsTable
import ink.doa.workbench.data.persistence.postgres.identity.TenantLoginMethodSettingsTable
import ink.doa.workbench.data.persistence.postgres.identity.TenantMembersTable
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import ink.doa.workbench.data.persistence.postgres.identity.UsersTable
import ink.doa.workbench.identity.LoginAccountStore
import ink.doa.workbench.identity.LoginDiscoveryRepository
import ink.doa.workbench.identity.UserLoginAccountRepository
import ink.doa.workbench.identity.common.summary.LoginMethodSummary
import ink.doa.workbench.identity.model.TenantLoginOption
import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.tenant.common.summary.TenantSummary
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

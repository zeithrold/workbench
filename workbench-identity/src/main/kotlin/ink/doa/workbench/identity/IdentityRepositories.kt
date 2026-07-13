package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.AuthEventRecord
import ink.doa.workbench.identity.model.CreateAuthEventCommand
import ink.doa.workbench.identity.model.CreateTenantMemberCommand
import ink.doa.workbench.identity.model.CreateUserCommand
import ink.doa.workbench.identity.model.TenantMemberRecord
import ink.doa.workbench.identity.model.UserRecord
import java.util.UUID

interface UserRepository {
  suspend fun create(command: CreateUserCommand): UserRecord

  suspend fun findById(id: UUID): UserRecord?

  suspend fun findByApiId(apiId: String): UserRecord?

  suspend fun findByPrimaryEmail(primaryEmail: String): UserRecord?
}

interface TenantMemberRepository {
  suspend fun create(command: CreateTenantMemberCommand): TenantMemberRecord

  suspend fun findByTenantAndUser(tenantId: UUID, userId: UUID): TenantMemberRecord?

  suspend fun listByUser(userId: UUID): List<TenantMemberRecord>
}

interface AuthEventRepository {
  suspend fun append(command: CreateAuthEventCommand): AuthEventRecord

  suspend fun listRecentByUser(userId: UUID, limit: Int): List<AuthEventRecord>

  suspend fun listRecentByLoginAccount(loginAccountId: UUID, limit: Int): List<AuthEventRecord>
}

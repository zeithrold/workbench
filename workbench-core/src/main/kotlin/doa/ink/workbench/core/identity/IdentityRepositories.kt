package doa.ink.workbench.core.identity

import doa.ink.workbench.core.identity.model.AuthEventRecord
import doa.ink.workbench.core.identity.model.CreateAuthEventCommand
import doa.ink.workbench.core.identity.model.CreateTenantMemberCommand
import doa.ink.workbench.core.identity.model.CreateUserCommand
import doa.ink.workbench.core.identity.model.TenantMemberRecord
import doa.ink.workbench.core.identity.model.UserRecord
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

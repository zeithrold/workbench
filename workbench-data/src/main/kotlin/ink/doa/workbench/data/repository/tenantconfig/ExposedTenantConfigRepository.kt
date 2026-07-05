package ink.doa.workbench.data.repository.tenantconfig

import ink.doa.workbench.core.tenantconfig.TenantConfigRepository
import ink.doa.workbench.core.tenantconfig.model.TenantConfigKey
import ink.doa.workbench.core.tenantconfig.model.TenantConfigRecord
import ink.doa.workbench.core.tenantconfig.model.UpsertTenantConfigCommand
import ink.doa.workbench.data.persistence.postgres.tenantconfig.TenantConfigEntriesTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedTenantConfigRepository(private val database: Database) : TenantConfigRepository {
  override suspend fun findByTenantAndKey(
    tenantId: UUID,
    key: TenantConfigKey,
  ): TenantConfigRecord? =
    suspendTransaction(db = database) {
      TenantConfigEntriesTable.selectAll()
        .where {
          (TenantConfigEntriesTable.tenantId eq tenantId.toKotlinUuid()) and
            (TenantConfigEntriesTable.key eq key.value)
        }
        .singleOrNull()
        ?.toTenantConfigRecord()
    }

  override suspend fun listByTenant(tenantId: UUID): List<TenantConfigRecord> =
    suspendTransaction(db = database) {
      TenantConfigEntriesTable.selectAll()
        .where { TenantConfigEntriesTable.tenantId eq tenantId.toKotlinUuid() }
        .orderBy(TenantConfigEntriesTable.key, SortOrder.ASC)
        .map { it.toTenantConfigRecord() }
    }

  override suspend fun upsert(command: UpsertTenantConfigCommand): TenantConfigRecord =
    suspendTransaction(db = database) {
      val now = nowUtc()
      val existing =
        TenantConfigEntriesTable.selectAll()
          .where {
            (TenantConfigEntriesTable.tenantId eq command.tenantId.toKotlinUuid()) and
              (TenantConfigEntriesTable.key eq command.key.value)
          }
          .singleOrNull()

      val id = existing?.get(TenantConfigEntriesTable.id)?.toJavaUuid() ?: UUID.randomUUID()
      if (existing == null) {
        TenantConfigEntriesTable.insert {
          it[TenantConfigEntriesTable.id] = id.toKotlinUuid()
          it[TenantConfigEntriesTable.tenantId] = command.tenantId.toKotlinUuid()
          it[TenantConfigEntriesTable.key] = command.key.value
          it[TenantConfigEntriesTable.value] = command.value
          it[TenantConfigEntriesTable.secretRef] = command.secretRef
          it[TenantConfigEntriesTable.createdBy] = command.actorUserId?.toKotlinUuid()
          it[TenantConfigEntriesTable.updatedBy] = command.actorUserId?.toKotlinUuid()
          it[TenantConfigEntriesTable.createdAt] = now
          it[TenantConfigEntriesTable.updatedAt] = now
        }
      } else {
        TenantConfigEntriesTable.update({ TenantConfigEntriesTable.id eq id.toKotlinUuid() }) {
          it[TenantConfigEntriesTable.value] = command.value
          it[TenantConfigEntriesTable.secretRef] = command.secretRef
          it[TenantConfigEntriesTable.updatedBy] = command.actorUserId?.toKotlinUuid()
          it[TenantConfigEntriesTable.updatedAt] = now
        }
      }

      TenantConfigEntriesTable.selectAll()
        .where { TenantConfigEntriesTable.id eq id.toKotlinUuid() }
        .single()
        .toTenantConfigRecord()
    }
}

private fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

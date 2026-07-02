package doa.ink.workbench.data.identity

import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.data.persistence.TenantsTable
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedTenantRepository(private val database: Database) : TenantRepository {
  override suspend fun findById(id: UUID): TenantRecord? =
    suspendTransaction(db = database) {
      TenantsTable.selectAll()
        .where {
          (TenantsTable.deletedAt.isNull()) and (TenantsTable.id eq id.toKotlinUuid())
        }
        .singleOrNull()
        ?.toTenantRecord()
    }

  override suspend fun findByApiId(apiId: String): TenantRecord? =
    suspendTransaction(db = database) {
      TenantsTable.selectAll()
        .where { (TenantsTable.deletedAt.isNull()) and (TenantsTable.apiId eq apiId) }
        .singleOrNull()
        ?.toTenantRecord()
    }

  override suspend fun findByIds(ids: Collection<UUID>): List<TenantRecord> =
    if (ids.isEmpty()) {
      emptyList()
    } else {
      suspendTransaction(db = database) {
        TenantsTable.selectAll()
          .where {
            (TenantsTable.deletedAt.isNull()) and
              (TenantsTable.id inList ids.map { it.toKotlinUuid() })
          }
          .map { it.toTenantRecord() }
      }
    }
}

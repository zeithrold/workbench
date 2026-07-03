package doa.ink.workbench.data.identity

import doa.ink.workbench.core.common.errors.ResourceConflictException
import doa.ink.workbench.core.common.errors.ResourceNotFoundException
import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.TenantRepository
import doa.ink.workbench.core.identity.model.CreateTenantCommand
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.UpdateTenantCommand
import doa.ink.workbench.data.persistence.TenantsTable
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedTenantRepository(private val database: Database) : TenantRepository {
  override suspend fun create(command: CreateTenantCommand): TenantRecord =
    suspendTransaction(db = database) {
      if (
        TenantsTable.selectAll()
          .where { (TenantsTable.deletedAt.isNull()) and (TenantsTable.slug eq command.slug) }
          .any()
      ) {
        throw ResourceConflictException("Tenant slug is already in use.")
      }
      val id = UUID.randomUUID()
      val apiId = PublicId.new("ten")
      val now = nowUtc()
      TenantsTable.insert {
        it[TenantsTable.id] = id.toKotlinUuid()
        it[TenantsTable.apiId] = apiId.value
        it[name] = command.name
        it[slug] = command.slug
        it[timezone] = command.timezone
        it[locale] = command.locale
        it[status] = command.status.dbValue
        it[createdAt] = now
        it[updatedAt] = now
      }
      TenantsTable.selectAll()
        .where { (TenantsTable.deletedAt.isNull()) and (TenantsTable.id eq id.toKotlinUuid()) }
        .single()
        .toTenantRecord()
    }

  override suspend fun update(command: UpdateTenantCommand): TenantRecord =
    suspendTransaction(db = database) {
      val existing =
        TenantsTable.selectAll()
          .where {
            (TenantsTable.deletedAt.isNull()) and
              (TenantsTable.id eq command.tenantId.toKotlinUuid())
          }
          .singleOrNull() ?: throw ResourceNotFoundException("Tenant not found.")
      command.slug?.let { slug ->
        if (
          slug != existing[TenantsTable.slug] &&
            TenantsTable.selectAll()
              .where { (TenantsTable.deletedAt.isNull()) and (TenantsTable.slug eq slug) }
              .any()
        ) {
          throw ResourceConflictException("Tenant slug is already in use.")
        }
      }
      val now = nowUtc()
      TenantsTable.update({ TenantsTable.id eq command.tenantId.toKotlinUuid() }) { row ->
        command.name?.let { row[name] = it }
        command.slug?.let { row[slug] = it }
        command.timezone?.let { row[timezone] = it }
        command.locale?.let { row[locale] = it }
        command.status?.let { row[status] = it.dbValue }
        row[updatedAt] = now
      }
      TenantsTable.selectAll()
        .where {
          (TenantsTable.deletedAt.isNull()) and (TenantsTable.id eq command.tenantId.toKotlinUuid())
        }
        .single()
        .toTenantRecord()
    }

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

  override suspend fun findBySlug(slug: String): TenantRecord? =
    suspendTransaction(db = database) {
      TenantsTable.selectAll()
        .where { (TenantsTable.deletedAt.isNull()) and (TenantsTable.slug eq slug) }
        .singleOrNull()
        ?.toTenantRecord()
    }

  override suspend fun existsBySlug(slug: String): Boolean =
    suspendTransaction(db = database) {
      TenantsTable.selectAll()
        .where { (TenantsTable.deletedAt.isNull()) and (TenantsTable.slug eq slug) }
        .any()
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

  override suspend fun list(slug: String?): List<TenantRecord> =
    suspendTransaction(db = database) {
      val query =
        if (slug == null) {
          TenantsTable.selectAll().where { TenantsTable.deletedAt.isNull() }
        } else {
          TenantsTable.selectAll().where {
            (TenantsTable.deletedAt.isNull()) and (TenantsTable.slug eq slug)
          }
        }
      query.orderBy(TenantsTable.createdAt to SortOrder.ASC).map { it.toTenantRecord() }
    }
}

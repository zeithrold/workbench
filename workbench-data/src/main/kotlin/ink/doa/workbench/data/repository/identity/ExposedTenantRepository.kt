package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.core.common.errors.ResourceConflictException
import ink.doa.workbench.core.common.errors.ResourceNotFoundException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.TenantRepository
import ink.doa.workbench.core.identity.model.CreateTenantCommand
import ink.doa.workbench.core.identity.model.FinalizeTenantDestroyCommand
import ink.doa.workbench.core.identity.model.TenantRecord
import ink.doa.workbench.core.identity.model.TenantStatus
import ink.doa.workbench.core.identity.model.UpdateTenantCommand
import ink.doa.workbench.core.messaging.EventMetadata
import ink.doa.workbench.core.port.messaging.DomainEventOutbox
import ink.doa.workbench.core.tenant.events.TenantDestroyRequestedEvent
import ink.doa.workbench.core.tenant.events.TenantDomainEvents
import ink.doa.workbench.data.persistence.postgres.identity.TenantsTable
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

@Repository
class ExposedTenantRepository(
  private val database: Database,
  private val outbox: DomainEventOutbox? = null,
) : TenantRepository {
  override suspend fun create(command: CreateTenantCommand): TenantRecord =
    suspendTransaction(db = database) {
      if (
        TenantsTable.selectAll()
          .where { operationalTenantFilter() and (TenantsTable.slug eq command.slug) }
          .any()
      ) {
        throw ResourceConflictException(WorkbenchErrorCode.TENANT_SLUG_IN_USE)
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
        .where { operationalTenantFilter() and (TenantsTable.id eq id.toKotlinUuid()) }
        .single()
        .toTenantRecord()
    }

  override suspend fun update(command: UpdateTenantCommand): TenantRecord =
    suspendTransaction(db = database) {
      val existing =
        TenantsTable.selectAll()
          .where {
            operationalTenantFilter() and (TenantsTable.id eq command.tenantId.toKotlinUuid())
          }
          .singleOrNull()
          ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND)
      command.slug?.let { slug ->
        if (
          slug != existing[TenantsTable.slug] &&
            TenantsTable.selectAll()
              .where { operationalTenantFilter() and (TenantsTable.slug eq slug) }
              .any()
        ) {
          throw ResourceConflictException(WorkbenchErrorCode.TENANT_SLUG_IN_USE)
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
          operationalTenantFilter() and (TenantsTable.id eq command.tenantId.toKotlinUuid())
        }
        .single()
        .toTenantRecord()
    }

  override suspend fun markDestroying(tenantId: UUID): TenantRecord =
    suspendTransaction(db = database) { markDestroyingWithinTransaction(tenantId) }

  override suspend fun requestDestroy(
    tenantId: UUID,
    tenantApiId: String,
    payload: TenantDestroyRequestedEvent,
  ): TenantRecord =
    suspendTransaction(db = database) {
      val destroying = markDestroyingWithinTransaction(tenantId)
      val outboxWriter = outbox ?: error("DomainEventOutbox is required for requestDestroy")
      outboxWriter.append(
        spec = TenantDomainEvents.DestroyRequested,
        key = tenantApiId,
        payload = payload,
        metadata = EventMetadata(tenantId = tenantApiId),
      )
      destroying
    }

  private fun markDestroyingWithinTransaction(tenantId: UUID): TenantRecord {
    val existing =
      TenantsTable.selectAll()
        .where {
          (TenantsTable.deletedAt.isNull()) and (TenantsTable.id eq tenantId.toKotlinUuid())
        }
        .singleOrNull()
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_TENANT_NOT_FOUND)
    val currentStatus = tenantStatusOf(existing[TenantsTable.status])
    when (currentStatus) {
      TenantStatus.DESTROYING ->
        throw ResourceConflictException(WorkbenchErrorCode.TENANT_ALREADY_DESTROYING)
      TenantStatus.ACTIVE,
      TenantStatus.PENDING_ACTIVATION -> Unit
    }
    val now = nowUtc()
    TenantsTable.update({ TenantsTable.id eq tenantId.toKotlinUuid() }) {
      it[status] = TenantStatus.DESTROYING.dbValue
      it[updatedAt] = now
    }
    return TenantsTable.selectAll()
      .where {
        (TenantsTable.deletedAt.isNull()) and (TenantsTable.id eq tenantId.toKotlinUuid())
      }
      .single()
      .toTenantRecord()
  }

  override suspend fun finalizeDestroy(command: FinalizeTenantDestroyCommand): Boolean =
    suspendTransaction(db = database) {
      val existing =
        TenantsTable.selectAll()
          .where {
            (TenantsTable.deletedAt.isNull()) and
              (TenantsTable.id eq command.tenantId.toKotlinUuid())
          }
          .singleOrNull() ?: return@suspendTransaction false
      if (existing[TenantsTable.deletedAt] != null) {
        return@suspendTransaction false
      }
      val now = nowUtc()
      TenantsTable.update({ TenantsTable.id eq command.tenantId.toKotlinUuid() }) {
        it[deletedAt] = now
        it[deletedBy] = command.deletedBy.toKotlinUuid()
        it[deleteReason] = command.deleteReason
        it[updatedAt] = now
      } > 0
    }

  override suspend fun findById(id: UUID): TenantRecord? =
    suspendTransaction(db = database) {
      TenantsTable.selectAll()
        .where { operationalTenantFilter() and (TenantsTable.id eq id.toKotlinUuid()) }
        .singleOrNull()
        ?.toTenantRecord()
    }

  override suspend fun findByIdForDestruction(id: UUID): TenantRecord? =
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
        .where { operationalTenantFilter() and (TenantsTable.apiId eq apiId) }
        .singleOrNull()
        ?.toTenantRecord()
    }

  override suspend fun findByApiIdForAdmin(apiId: String): TenantRecord? =
    suspendTransaction(db = database) {
      TenantsTable.selectAll()
        .where { (TenantsTable.deletedAt.isNull()) and (TenantsTable.apiId eq apiId) }
        .singleOrNull()
        ?.toTenantRecord()
    }

  override suspend fun findBySlug(slug: String): TenantRecord? =
    suspendTransaction(db = database) {
      TenantsTable.selectAll()
        .where { operationalTenantFilter() and (TenantsTable.slug eq slug) }
        .singleOrNull()
        ?.toTenantRecord()
    }

  override suspend fun existsBySlug(slug: String): Boolean =
    suspendTransaction(db = database) {
      TenantsTable.selectAll()
        .where { operationalTenantFilter() and (TenantsTable.slug eq slug) }
        .any()
    }

  override suspend fun findByIds(ids: Collection<UUID>): List<TenantRecord> =
    if (ids.isEmpty()) {
      emptyList()
    } else {
      suspendTransaction(db = database) {
        TenantsTable.selectAll()
          .where {
            operationalTenantFilter() and (TenantsTable.id inList ids.map { it.toKotlinUuid() })
          }
          .map { it.toTenantRecord() }
      }
    }

  override suspend fun list(slug: String?): List<TenantRecord> =
    suspendTransaction(db = database) {
      val query =
        if (slug == null) {
          TenantsTable.selectAll().where { operationalTenantFilter() }
        } else {
          TenantsTable.selectAll().where {
            operationalTenantFilter() and (TenantsTable.slug eq slug)
          }
        }
      query.orderBy(TenantsTable.createdAt to SortOrder.ASC).map { it.toTenantRecord() }
    }

  override suspend fun listForAdmin(slug: String?): List<TenantRecord> =
    suspendTransaction(db = database) {
      val baseFilter = TenantsTable.deletedAt.isNull()
      val query =
        if (slug == null) {
          TenantsTable.selectAll().where { baseFilter }
        } else {
          TenantsTable.selectAll().where { baseFilter and (TenantsTable.slug eq slug) }
        }
      query.orderBy(TenantsTable.createdAt to SortOrder.ASC).map { it.toTenantRecord() }
    }

  private fun operationalTenantFilter(): Op<Boolean> =
    TenantsTable.deletedAt.isNull() and (TenantsTable.status neq TenantStatus.DESTROYING.dbValue)
}

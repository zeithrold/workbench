package doa.ink.workbench.data.identity

import doa.ink.workbench.core.identity.TenantLoginMethodSettingRepository
import doa.ink.workbench.core.identity.model.CreateTenantLoginMethodSettingCommand
import doa.ink.workbench.core.identity.model.TenantLoginMethodSettingRecord
import doa.ink.workbench.data.persistence.TenantLoginMethodSettingsTable
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.springframework.stereotype.Repository

@Repository
class ExposedTenantLoginMethodSettingRepository(private val database: Database) :
  TenantLoginMethodSettingRepository {
  override suspend fun createTenantSetting(
    command: CreateTenantLoginMethodSettingCommand
  ): TenantLoginMethodSettingRecord =
    suspendTransaction(db = database) {
      val id = UUID.randomUUID()
      val now = nowUtc()
      TenantLoginMethodSettingsTable.insert {
        it[TenantLoginMethodSettingsTable.id] = id.toKotlinUuid()
        it[TenantLoginMethodSettingsTable.tenantId] = command.tenantId.toKotlinUuid()
        it[TenantLoginMethodSettingsTable.loginMethodId] = command.loginMethodId.toKotlinUuid()
        it[TenantLoginMethodSettingsTable.isEnabled] = command.isEnabled
        it[TenantLoginMethodSettingsTable.allowSignup] = command.allowSignup
        it[TenantLoginMethodSettingsTable.displayOrder] = command.displayOrder
        it[TenantLoginMethodSettingsTable.config] = command.config
        it[TenantLoginMethodSettingsTable.secretRef] = command.secretRef
        it[TenantLoginMethodSettingsTable.createdBy] = command.createdBy?.toKotlinUuid()
        it[TenantLoginMethodSettingsTable.updatedBy] = command.updatedBy?.toKotlinUuid()
        it[TenantLoginMethodSettingsTable.createdAt] = now
        it[TenantLoginMethodSettingsTable.updatedAt] = now
      }
      TenantLoginMethodSettingsTable.selectAll()
        .where { TenantLoginMethodSettingsTable.id eq id.toKotlinUuid() }
        .single()
        .toTenantLoginMethodSettingRecord()
    }

  override suspend fun findTenantSetting(
    tenantId: UUID,
    loginMethodId: UUID,
  ): TenantLoginMethodSettingRecord? =
    suspendTransaction(db = database) {
      TenantLoginMethodSettingsTable.selectAll()
        .where {
          (TenantLoginMethodSettingsTable.tenantId eq tenantId.toKotlinUuid()) and
            (TenantLoginMethodSettingsTable.loginMethodId eq loginMethodId.toKotlinUuid())
        }
        .singleOrNull()
        ?.toTenantLoginMethodSettingRecord()
    }
}

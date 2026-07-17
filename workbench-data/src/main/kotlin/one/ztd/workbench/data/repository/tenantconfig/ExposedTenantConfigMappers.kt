package one.ztd.workbench.data.repository.tenantconfig

import kotlin.uuid.toJavaUuid
import one.ztd.workbench.data.persistence.postgres.tenantconfig.TenantConfigEntriesTable
import one.ztd.workbench.tenant.tenantconfig.model.TenantConfigKey
import one.ztd.workbench.tenant.tenantconfig.model.TenantConfigRecord
import org.jetbrains.exposed.v1.core.ResultRow

internal fun ResultRow.toTenantConfigRecord() =
  TenantConfigRecord(
    id = this[TenantConfigEntriesTable.id].toJavaUuid(),
    tenantId = this[TenantConfigEntriesTable.tenantId].toJavaUuid(),
    key = TenantConfigKey(this[TenantConfigEntriesTable.key]),
    value = this[TenantConfigEntriesTable.value],
    secretRef = this[TenantConfigEntriesTable.secretRef],
    createdBy = this[TenantConfigEntriesTable.createdBy]?.toJavaUuid(),
    updatedBy = this[TenantConfigEntriesTable.updatedBy]?.toJavaUuid(),
    createdAt = this[TenantConfigEntriesTable.createdAt],
    updatedAt = this[TenantConfigEntriesTable.updatedAt],
  )

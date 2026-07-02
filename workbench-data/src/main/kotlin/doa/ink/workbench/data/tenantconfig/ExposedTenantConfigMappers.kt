package doa.ink.workbench.data.tenantconfig

import doa.ink.workbench.core.tenantconfig.model.TenantConfigKey
import doa.ink.workbench.core.tenantconfig.model.TenantConfigRecord
import doa.ink.workbench.data.persistence.TenantConfigEntriesTable
import kotlin.uuid.toJavaUuid
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

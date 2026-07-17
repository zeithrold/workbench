package one.ztd.workbench.tenant.tenant.events

import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import one.ztd.workbench.tenant.model.TenantRecord

@Serializable
data class TenantDestroyedEvent(
  val tenantId: String,
  val deletedAt: String,
  val deleteReason: String?,
) {
  companion object {
    fun from(
      tenant: TenantRecord,
      deletedAt: OffsetDateTime,
      deleteReason: String?,
    ): TenantDestroyedEvent =
      TenantDestroyedEvent(
        tenantId = tenant.apiId.value,
        deletedAt = deletedAt.toString(),
        deleteReason = deleteReason,
      )
  }
}

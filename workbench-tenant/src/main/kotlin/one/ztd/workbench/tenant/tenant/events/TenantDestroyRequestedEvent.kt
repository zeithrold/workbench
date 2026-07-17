package one.ztd.workbench.tenant.tenant.events

import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.model.TenantRecord

@Serializable
data class TenantDestroyRequestedEvent(
  val tenantId: String,
  val requestedBy: String,
  val deleteReason: String?,
  val requestedAt: String,
) {
  companion object {
    fun from(
      tenant: TenantRecord,
      deleteReason: String?,
      requestedAt: OffsetDateTime,
      requestedByPublicId: PublicId,
    ): TenantDestroyRequestedEvent =
      TenantDestroyRequestedEvent(
        tenantId = tenant.apiId.value,
        requestedBy = requestedByPublicId.value,
        deleteReason = deleteReason,
        requestedAt = requestedAt.toString(),
      )
  }
}

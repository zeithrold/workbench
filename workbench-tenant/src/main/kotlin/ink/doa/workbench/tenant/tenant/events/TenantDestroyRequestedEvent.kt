package ink.doa.workbench.tenant.tenant.events

import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.tenant.model.TenantRecord
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable

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

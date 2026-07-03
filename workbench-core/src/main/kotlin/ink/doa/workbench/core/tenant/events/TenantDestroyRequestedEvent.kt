package ink.doa.workbench.core.tenant.events

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.TenantRecord
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

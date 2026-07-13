package ink.doa.workbench.tenant.model

import ink.doa.workbench.kernel.common.ids.PublicId
import java.time.OffsetDateTime
import java.util.UUID

data class TenantRecord(
  val id: UUID,
  val apiId: PublicId,
  val slug: String,
  val name: String,
  val timezone: String = "UTC",
  val locale: String = "en-US",
  val status: TenantStatus = TenantStatus.ACTIVE,
  val createdAt: OffsetDateTime? = null,
  val updatedAt: OffsetDateTime? = null,
)

enum class TenantStatus(val dbValue: String) {
  ACTIVE("active"),
  PENDING_ACTIVATION("pending_activation"),
  DESTROYING("destroying"),
}

data class CreateTenantCommand(
  val name: String,
  val slug: String,
  val timezone: String = "UTC",
  val locale: String = "en-US",
  val status: TenantStatus = TenantStatus.ACTIVE,
)

data class UpdateTenantCommand(
  val tenantId: UUID,
  val name: String? = null,
  val slug: String? = null,
  val timezone: String? = null,
  val locale: String? = null,
  val status: TenantStatus? = null,
)

data class RequestTenantDestroyCommand(
  val tenantId: UUID,
  val requestedBy: UUID,
  val deleteReason: String? = null,
)

data class FinalizeTenantDestroyCommand(
  val tenantId: UUID,
  val deletedBy: UUID,
  val deleteReason: String?,
)

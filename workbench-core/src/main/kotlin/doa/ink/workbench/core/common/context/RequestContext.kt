package doa.ink.workbench.core.common.context

import doa.ink.workbench.core.common.ids.PublicId
import java.time.Instant
import java.util.UUID

data class RequestContext(
  val requestId: String,
  val apiVersion: ApiVersion,
  val actorUserId: UUID?,
  val actorPublicId: PublicId?,
  val receivedAt: Instant,
)

data class TenantRequestContext(
  val base: RequestContext,
  val tenantId: UUID,
  val tenantPublicId: PublicId,
)

@JvmInline
value class ApiVersion(val value: String) {
  init {
    require(value.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
      "API version must be a date in yyyy-MM-dd format."
    }
  }

  companion object {
    val Default = ApiVersion("2026-07-02")
    const val HeaderName = "X-Workbench-API-Version"
  }
}

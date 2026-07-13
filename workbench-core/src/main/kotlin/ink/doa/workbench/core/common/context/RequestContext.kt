package ink.doa.workbench.core.common.context

import java.time.Instant

sealed interface ScopedRequestContext {
  val requestId: String
  val apiVersion: ApiVersion
  val actor: UserContextSummary?
  val receivedAt: Instant
}

sealed interface InstanceScopedRequestContext : ScopedRequestContext {
  val instance: InstanceContextSummary
}

data class RequestContext(
  override val requestId: String,
  override val apiVersion: ApiVersion,
  override val actor: UserContextSummary?,
  override val receivedAt: Instant,
) : ScopedRequestContext

data class InstanceRequestContext(
  override val requestId: String,
  override val apiVersion: ApiVersion,
  override val actor: UserContextSummary?,
  override val receivedAt: Instant,
  override val instance: InstanceContextSummary,
) : InstanceScopedRequestContext

data class TenantRequestContext(
  override val requestId: String,
  override val apiVersion: ApiVersion,
  override val actor: UserContextSummary?,
  override val receivedAt: Instant,
  override val instance: InstanceContextSummary,
  val tenant: TenantContextSummary,
) : InstanceScopedRequestContext

data class ProjectRequestContext(
  override val requestId: String,
  override val apiVersion: ApiVersion,
  override val actor: UserContextSummary?,
  override val receivedAt: Instant,
  override val instance: InstanceContextSummary,
  val tenant: TenantContextSummary,
  val project: ProjectContextSummary,
) : InstanceScopedRequestContext

@JvmInline
value class ApiVersion(val value: String) {
  init {
    require(value.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
      "API version must be a date in yyyy-MM-dd format."
    }
  }

  companion object {
    val Default = ApiVersion("2026-07-13")
    const val HeaderName = "X-Workbench-API-Version"
  }
}

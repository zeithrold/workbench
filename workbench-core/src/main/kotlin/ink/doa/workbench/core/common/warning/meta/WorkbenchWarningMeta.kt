package ink.doa.workbench.core.common.warning.meta

import ink.doa.workbench.core.common.summary.ProjectSummary
import ink.doa.workbench.core.common.summary.TenantSummary

sealed interface WorkbenchWarningMeta {
  val kind: String
}

data class ProjectDestroyScheduledMeta(
  val project: ProjectSummary,
  val deleteReason: String? = null,
) : WorkbenchWarningMeta {
  override val kind: String = "projectDestroyScheduled"
}

data class TenantDestroyScheduledMeta(
  val tenant: TenantSummary,
  val deleteReason: String? = null,
) : WorkbenchWarningMeta {
  override val kind: String = "tenantDestroyScheduled"
}

data class ApiVersionDeprecatedMeta(
  val requestedVersion: String,
  val currentVersion: String,
  val sunsetOn: String?,
) : WorkbenchWarningMeta {
  override val kind: String = "apiVersionDeprecated"
}

data object WarningTruncatedMeta : WorkbenchWarningMeta {
  override val kind: String = "warningTruncated"
}

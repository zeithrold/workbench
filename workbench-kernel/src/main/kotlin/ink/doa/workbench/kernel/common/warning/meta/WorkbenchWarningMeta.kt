package ink.doa.workbench.kernel.common.warning.meta

import ink.doa.workbench.kernel.common.ids.PublicId

sealed interface WorkbenchWarningMeta {
  val kind: String
}

data class ProjectDestroyScheduledMeta(
  val project: ProjectWarningEmbed,
  val deleteReason: String? = null,
) : WorkbenchWarningMeta {
  override val kind: String = "projectDestroyScheduled"
}

data class TenantDestroyScheduledMeta(
  val tenant: TenantWarningEmbed,
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

interface ProjectWarningEmbed {
  val id: PublicId
  val identifier: String
  val name: String
}

interface TenantWarningEmbed {
  val id: PublicId
  val name: String
  val slug: String
}

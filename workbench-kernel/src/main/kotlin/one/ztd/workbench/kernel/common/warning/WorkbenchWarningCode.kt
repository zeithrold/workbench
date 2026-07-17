package one.ztd.workbench.kernel.common.warning

import kotlin.reflect.KClass
import one.ztd.workbench.kernel.common.warning.meta.ApiVersionDeprecatedMeta
import one.ztd.workbench.kernel.common.warning.meta.ProjectDestroyScheduledMeta
import one.ztd.workbench.kernel.common.warning.meta.TenantDestroyScheduledMeta
import one.ztd.workbench.kernel.common.warning.meta.WarningTruncatedMeta
import one.ztd.workbench.kernel.common.warning.meta.WorkbenchWarningMeta

private val WorkbenchWarningCodePattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

enum class WorkbenchWarningCode(
  val code: String,
  val defaultMessage: String,
  val defaultSeverity: WorkbenchWarningSeverity,
  val metaType: KClass<out WorkbenchWarningMeta>,
) {
  PROJECT_DESTROY_SCHEDULED(
    code = "project.destroy.scheduled",
    defaultMessage = "Project destruction has been scheduled.",
    defaultSeverity = WorkbenchWarningSeverity.RISK,
    metaType = ProjectDestroyScheduledMeta::class,
  ),
  TENANT_DESTROY_SCHEDULED(
    code = "tenant.destroy.scheduled",
    defaultMessage = "Tenant destruction has been scheduled.",
    defaultSeverity = WorkbenchWarningSeverity.RISK,
    metaType = TenantDestroyScheduledMeta::class,
  ),
  API_VERSION_DEPRECATED(
    code = "api.version.deprecated",
    defaultMessage = "The requested API version is deprecated.",
    defaultSeverity = WorkbenchWarningSeverity.CAUTION,
    metaType = ApiVersionDeprecatedMeta::class,
  ),
  WARNING_TRUNCATED(
    code = "warning.truncated",
    defaultMessage = "Additional warnings were omitted from the response header.",
    defaultSeverity = WorkbenchWarningSeverity.INFO,
    metaType = WarningTruncatedMeta::class,
  );

  init {
    require(code.matches(WorkbenchWarningCodePattern)) {
      "Workbench warning code must be dot-separated lower-case words."
    }
  }
}

package one.ztd.workbench.kernel.common.warning

import one.ztd.workbench.kernel.common.warning.meta.WorkbenchWarningMeta

data class WorkbenchWarning(
  val code: WorkbenchWarningCode,
  val message: String,
  val severity: WorkbenchWarningSeverity,
  val meta: WorkbenchWarningMeta,
)

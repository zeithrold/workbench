package ink.doa.workbench.core.common.warning

import ink.doa.workbench.core.common.warning.meta.WorkbenchWarningMeta

data class WorkbenchWarning(
  val code: WorkbenchWarningCode,
  val message: String,
  val severity: WorkbenchWarningSeverity,
  val meta: WorkbenchWarningMeta,
)

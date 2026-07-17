package one.ztd.workbench.kernel.common.warning

import one.ztd.workbench.kernel.common.warning.meta.WorkbenchWarningMeta

interface WorkbenchWarningCollector {
  fun warn(code: WorkbenchWarningCode, meta: WorkbenchWarningMeta, message: String? = null)

  fun warn(warning: WorkbenchWarning)

  fun drain(): List<WorkbenchWarning>
}

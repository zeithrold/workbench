package one.ztd.workbench.web.api.warning

import one.ztd.workbench.kernel.common.warning.WorkbenchWarning
import one.ztd.workbench.kernel.common.warning.WorkbenchWarningCode
import one.ztd.workbench.kernel.common.warning.WorkbenchWarningCollector
import one.ztd.workbench.kernel.common.warning.WorkbenchWarningMetaValidator
import one.ztd.workbench.kernel.common.warning.meta.WorkbenchWarningMeta

class RequestScopedWorkbenchWarningCollector : WorkbenchWarningCollector {
  private val warnings = mutableListOf<WorkbenchWarning>()
  private val seen = linkedSetOf<WorkbenchWarning>()

  override fun warn(code: WorkbenchWarningCode, meta: WorkbenchWarningMeta, message: String?) {
    warn(
      WorkbenchWarning(
        code = code,
        message = message ?: code.defaultMessage,
        severity = code.defaultSeverity,
        meta = meta,
      )
    )
  }

  override fun warn(warning: WorkbenchWarning) {
    WorkbenchWarningMetaValidator.validate(warning.code, warning.meta)
    if (seen.add(warning)) {
      warnings.add(warning)
    }
  }

  override fun drain(): List<WorkbenchWarning> {
    val drained = warnings.toList()
    warnings.clear()
    seen.clear()
    return drained
  }
}

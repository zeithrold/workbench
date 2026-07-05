package ink.doa.workbench.web.api.warning

import ink.doa.workbench.core.common.warning.WorkbenchWarning
import ink.doa.workbench.core.common.warning.WorkbenchWarningCode
import ink.doa.workbench.core.common.warning.WorkbenchWarningCollector
import ink.doa.workbench.core.common.warning.WorkbenchWarningMetaValidator
import ink.doa.workbench.core.common.warning.meta.WorkbenchWarningMeta

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

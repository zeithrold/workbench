package ink.doa.workbench.core.common.warning

enum class WorkbenchWarningSeverity(val jsonValue: String) {
  INFO("info"),
  CAUTION("caution"),
  RISK("risk");

  init {
    require(jsonValue.matches(Regex("^[a-z]+$"))) {
      "Warning severity JSON value must be lowercase."
    }
  }
}

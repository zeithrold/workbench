package ink.doa.workbench.core.sprint.model

enum class SprintStatus(val dbValue: String) {
  PLANNED("planned"),
  ACTIVE("active"),
  CLOSED("closed");

  companion object {
    fun fromDbValue(value: String): SprintStatus = entries.single {
      it.dbValue == value.lowercase()
    }
  }
}

package ink.doa.workbench.agile.workitem.query

data class WorkItemSearchGroupScope(
  val includeGroupKeys: List<WorkItemGroupKey> = emptyList(),
  val excludeGroupKeys: List<WorkItemGroupKey> = emptyList(),
) {
  init {
    require(includeGroupKeys.isEmpty() || excludeGroupKeys.isEmpty()) {
      "Work item search cannot combine includeGroupKeys and excludeGroupKeys."
    }
    require(includeGroupKeys.size <= MAX_KEYS) {
      "Work item search includeGroupKeys exceeds maximum of $MAX_KEYS."
    }
    require(excludeGroupKeys.size <= MAX_KEYS) {
      "Work item search excludeGroupKeys exceeds maximum of $MAX_KEYS."
    }
  }

  companion object {
    const val MAX_KEYS = 100
  }
}

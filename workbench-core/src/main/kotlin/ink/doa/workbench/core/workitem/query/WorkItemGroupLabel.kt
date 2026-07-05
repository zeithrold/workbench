package ink.doa.workbench.core.workitem.query

sealed interface WorkItemGroupLabel {
  data class Text(val text: String) : WorkItemGroupLabel

  data class Message(
    val code: String,
    val args: Map<String, String> = emptyMap(),
    val defaultMessage: String,
  ) : WorkItemGroupLabel
}

package one.ztd.workbench.agile.workitem.query

private val WorkItemGroupLabelCodePattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

enum class WorkItemGroupLabelCode(
  val code: String,
  val defaultMessage: String,
) {
  EMPTY_ASSIGNEE(
    code = "work_item.group.empty.assignee",
    defaultMessage = "Unassigned",
  ),
  EMPTY_PRIORITY(
    code = "work_item.group.empty.priority",
    defaultMessage = "No priority",
  ),
  EMPTY_SPRINT(
    code = "work_item.group.empty.sprint",
    defaultMessage = "No sprint",
  ),
  EMPTY_PROPERTY_USER(
    code = "work_item.group.empty.property_user",
    defaultMessage = "Unassigned",
  ),
  EMPTY_PROPERTY_OPTION(
    code = "work_item.group.empty.property_option",
    defaultMessage = "No option",
  ),
  EMPTY_GENERIC(
    code = "work_item.group.empty",
    defaultMessage = "Empty",
  );

  init {
    require(code.matches(WorkItemGroupLabelCodePattern)) {
      "Work item group label code must be dot-separated lower-case words."
    }
  }

  fun toLabel(args: Map<String, String> = emptyMap()): WorkItemGroupLabel.Message =
    WorkItemGroupLabel.Message(code = code, args = args, defaultMessage = defaultMessage)
}

package ink.doa.workbench.core.workitem.template

object TemplateSystemFields {
  val WRITABLE: Set<String> = setOf("title", "description", "assignee", "priority", "sprint")

  fun isWritableSystemField(name: String): Boolean = name in WRITABLE
}

package ink.doa.workbench.core.project.model

enum class ProjectStatus(val dbValue: String) {
  ACTIVE("active"),
  ARCHIVED("archived"),
  DESTROYING("destroying"),
}

enum class NonMemberVisibility(val dbValue: String) {
  INVISIBLE("invisible"),
  READ_ONLY("read_only"),
  READ_WRITE("read_write"),
}

enum class NonMemberJoinPolicy(val dbValue: String) {
  OPEN("open"),
  ADMIN_ONLY("admin_only"),
}

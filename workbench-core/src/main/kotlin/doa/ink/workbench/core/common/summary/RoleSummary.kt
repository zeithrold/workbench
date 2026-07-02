package doa.ink.workbench.core.common.summary

import doa.ink.workbench.core.permission.RoleRecord

data class RoleSummary(
  val id: String,
  val code: String,
  val name: String,
) {
  companion object {
    fun from(record: RoleRecord): RoleSummary =
      RoleSummary(
        id = record.apiId.value,
        code = record.code,
        name = record.name,
      )
  }
}

package ink.doa.workbench.identity.common.summary

import ink.doa.workbench.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.identity.model.LoginMethodKind

data class LoginMethodSummary(
  val id: String,
  val code: String,
  val kind: LoginMethodKind,
  val name: String,
) {
  companion object {
    fun from(record: LoginMethodDefinitionRecord): LoginMethodSummary =
      LoginMethodSummary(
        id = record.apiId.value,
        code = record.code,
        kind = record.kind,
        name = record.name,
      )
  }
}

package ink.doa.workbench.core.common.summary

import ink.doa.workbench.core.identity.model.LoginMethodDefinitionRecord
import ink.doa.workbench.core.identity.model.LoginMethodKind

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

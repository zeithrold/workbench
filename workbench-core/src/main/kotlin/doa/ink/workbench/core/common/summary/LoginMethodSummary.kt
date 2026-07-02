package doa.ink.workbench.core.common.summary

import doa.ink.workbench.core.identity.model.LoginMethodDefinitionRecord
import doa.ink.workbench.core.identity.model.LoginMethodKind

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

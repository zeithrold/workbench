package doa.ink.workbench.core.common.ids

import com.github.f4b6a3.ulid.UlidCreator

@JvmInline
value class PublicId(val value: String) {
  init {
    require(value.matches(PUBLIC_ID_PATTERN)) {
      "Public id must use a typed prefix and ULID suffix."
    }
  }

  override fun toString(): String = value

  companion object {
    private val PUBLIC_ID_PATTERN = Regex("^[a-z]{3}_[0-9A-HJKMNP-TV-Z]{26}$")

    fun new(prefix: String): PublicId {
      require(prefix.matches(Regex("^[a-z]{3}$"))) {
        "Public id prefix must be three lowercase letters."
      }
      return PublicId("${prefix}_${UlidCreator.getMonotonicUlid()}")
    }
  }
}

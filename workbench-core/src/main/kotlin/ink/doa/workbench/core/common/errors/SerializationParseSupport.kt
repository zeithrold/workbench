package ink.doa.workbench.core.common.errors

import kotlinx.serialization.SerializationException

object SerializationParseSupport {
  inline fun <T> parseOrThrow(
    block: () -> T,
    lazyError: (SerializationException) -> InvalidRequestException,
  ): T =
    try {
      block()
    } catch (ex: SerializationException) {
      throw lazyError(ex)
    }
}

package doa.ink.workbench.service.identity.auth

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonObject.stringValue(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

internal fun encodeQueryParam(key: String, value: String): String =
  "${encodeQueryValue(key)}=${encodeQueryValue(value)}"

internal fun encodeQueryValue(value: String): String =
  URLEncoder.encode(value, StandardCharsets.UTF_8)

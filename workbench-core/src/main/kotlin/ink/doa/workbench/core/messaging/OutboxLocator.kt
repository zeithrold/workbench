package ink.doa.workbench.core.messaging

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OutboxLocator(
  val version: Int = CURRENT_VERSION,
  val outboxId: String,
  val epoch: String,
) {
  init {
    require(version == CURRENT_VERSION) { "Unsupported outbox locator version: $version" }
    UUID.fromString(outboxId)
    require(epoch.isNotBlank()) { "Outbox locator epoch must not be blank" }
  }

  companion object {
    const val CURRENT_VERSION = 1
    private val json = Json { ignoreUnknownKeys = false }

    fun encode(outboxId: UUID, epoch: String): String =
      json.encodeToString(
        serializer(),
        OutboxLocator(outboxId = outboxId.toString(), epoch = epoch),
      )

    fun decode(value: String): OutboxLocator = json.decodeFromString(serializer(), value)
  }
}

package ink.doa.workbench.application.jobs.messaging

import ink.doa.workbench.kernel.messaging.DomainEventDecoder
import ink.doa.workbench.kernel.messaging.DomainEventEncoder
import ink.doa.workbench.kernel.messaging.DomainEventEnvelope
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class DomainEventDispatcherTest :
  StringSpec({
    "dispatch fans out and can target one consumer" {
      val handled = mutableListOf<String>()
      val first = recordingRegistration("first", handled)
      val second = recordingRegistration("second", handled)
      val dispatcher = DomainEventDispatcher(DomainEventDecoder(), listOf(first, second))
      val encoded = encodedEnvelope("example.updated")

      runBlocking { dispatcher.dispatch(encoded) }
      handled shouldContainExactly listOf("first", "second")

      handled.clear()
      runBlocking { dispatcher.dispatch(encoded, "second") }
      handled shouldContainExactly listOf("second")
      dispatcher.subscriptions().map { it.consumerName } shouldContainExactly
        listOf("first", "second")
    }

    "dispatch ignores events without a registration" {
      val handled = mutableListOf<String>()
      val dispatcher =
        DomainEventDispatcher(DomainEventDecoder(), listOf(recordingRegistration("first", handled)))

      runBlocking { dispatcher.dispatch(encodedEnvelope("example.deleted")) }

      handled.isEmpty() shouldBe true
    }
  })

private fun recordingRegistration(name: String, handled: MutableList<String>) =
  object : JobRegistration {
    override val consumerName = name
    override val topic = "example"
    override val eventTypes = setOf("example.updated")

    override suspend fun handle(envelope: DomainEventEnvelope) {
      handled += consumerName
    }
  }

private fun encodedEnvelope(type: String): String =
  DomainEventEncoder.defaultJson.encodeToString(
    DomainEventEnvelope(
      eventId = "evt_1",
      type = type,
      occurredAt = "2026-07-12T00:00:00Z",
      payload = JsonObject(emptyMap()),
    )
  )

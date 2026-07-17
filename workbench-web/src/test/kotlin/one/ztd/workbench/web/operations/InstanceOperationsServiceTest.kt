package one.ztd.workbench.web.operations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import one.ztd.workbench.application.jobs.messaging.MessagingProperties
import one.ztd.workbench.application.operations.DeliveryStatusCount
import one.ztd.workbench.application.operations.DeliveryTrendPoint
import one.ztd.workbench.application.operations.OperationsDataStore
import one.ztd.workbench.kernel.common.context.InstanceContextSummary
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.info.BuildProperties

class InstanceOperationsServiceTest :
  StringSpec({
    "aggregates component health metrics and delivery data" {
      val health = mockk<HealthEndpoint>()
      every { health.healthForPath(any()) } returns
        mockk {
          every { status.code } returns "UP"
        }
      val meters = SimpleMeterRegistry()
      Gauge.builder("jvm.memory.used", AtomicInteger(12)) { it.get().toDouble() }.register(meters)
      Timer.builder("http.server.requests").register(meters).record(Duration.ofMillis(5))
      val data = mockk<OperationsDataStore>()
      val trend =
        listOf(
          DeliveryTrendPoint(
            OffsetDateTime.parse("2026-07-14T23:00:00Z"),
            succeeded = 4,
            failed = 1,
          )
        )
      every { data.deliveryStatusCounts() } returns listOf(DeliveryStatusCount("pending", 3))
      every { data.deliveryTrendSince(any()) } returns trend
      val builds = mockk<ObjectProvider<BuildProperties>>()
      every { builds.ifAvailable } returns null
      val service =
        InstanceOperationsService(
          health,
          meters,
          data,
          MessagingProperties(),
          builds,
          Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC),
        )

      val result = service.snapshot(INSTANCE, "2026-07-15")

      result.status shouldBe "UP"
      result.components.map { it.status }.toSet() shouldBe setOf("UP")
      result.metrics.getValue("jvm.memory.used") shouldBe 12.0
      result.metrics.getValue("http.server.requests") shouldBe 1.0
      result.deliveries shouldBe mapOf("pending" to 3L)
      result.deliveryTrend shouldBe trend
    }

    "keeps the snapshot available when component probes fail" {
      val health = mockk<HealthEndpoint>()
      every { health.healthForPath(any()) } throws IllegalStateException("probe failed")
      val data = mockk<OperationsDataStore>()
      every { data.deliveryStatusCounts() } returns emptyList()
      every { data.deliveryTrendSince(any()) } returns emptyList()
      val builds = mockk<ObjectProvider<BuildProperties>>()
      every { builds.ifAvailable } returns null
      val service =
        InstanceOperationsService(
          health,
          SimpleMeterRegistry(),
          data,
          MessagingProperties(),
          builds,
          Clock.systemUTC(),
        )

      service.snapshot(INSTANCE, "2026-07-15").status shouldBe "UNKNOWN"
    }
  }) {
  private companion object {
    val INSTANCE =
      InstanceContextSummary(
        id = "ins_01JABCDEFGHJKMNPQRSTVWXYZ0",
        name = "Workbench",
      )
  }
}

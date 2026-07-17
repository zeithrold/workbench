package one.ztd.workbench.web.operations

import io.micrometer.core.instrument.MeterRegistry
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import java.lang.management.ManagementFactory
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import one.ztd.workbench.application.jobs.messaging.MessagingProperties
import one.ztd.workbench.application.operations.DeliveryTrendPoint
import one.ztd.workbench.application.operations.OperationsDataStore
import one.ztd.workbench.kernel.common.context.InstanceContextSummary
import one.ztd.workbench.web.api.Authenticated
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.InstanceScoped
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.context.InstanceRequestContext
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class InfrastructureComponentResponse(
  val code: String,
  val name: String,
  val status: String,
  val connection: String,
)

data class InstanceOperationsResponse(
  val instance: InstanceContextSummary,
  val version: String?,
  val apiVersion: String,
  val uptimeSeconds: Long,
  val messagingTransport: String,
  val status: String,
  val components: List<InfrastructureComponentResponse>,
  val metrics: Map<String, Double>,
  val deliveries: Map<String, Long>,
  val deliveryTrend: List<DeliveryTrendPoint>,
  val checkedAt: OffsetDateTime,
)

@Service
class InstanceOperationsService(
  private val health: HealthEndpoint,
  private val meters: MeterRegistry,
  private val data: OperationsDataStore,
  private val messaging: MessagingProperties,
  private val builds: ObjectProvider<BuildProperties>,
  private val clock: Clock,
) {
  fun snapshot(
    instance: InstanceContextSummary,
    apiVersion: String,
  ): InstanceOperationsResponse {
    val components =
      listOf(
        component("postgresql", "PostgreSQL", "db"),
        component("valkey", "Valkey", "redis"),
        component("redpanda", "Redpanda", "kafka"),
        component("minio", "MinIO", "s3"),
      )
    val overall =
      when {
        components.any { it.status == "DOWN" } -> "DEGRADED"
        components.all { it.status == "UP" } -> "UP"
        else -> "UNKNOWN"
      }
    return InstanceOperationsResponse(
      instance = instance,
      version = builds.ifAvailable?.version,
      apiVersion = apiVersion,
      uptimeSeconds = ManagementFactory.getRuntimeMXBean().uptime / 1000,
      messagingTransport = messaging.transport.name,
      status = overall,
      components = components,
      metrics =
        mapOf(
          "jvm.memory.used" to meter("jvm.memory.used"),
          "jvm.memory.max" to meter("jvm.memory.max"),
          "jvm.threads.live" to meter("jvm.threads.live"),
          "process.cpu.usage" to meter("process.cpu.usage"),
          "http.server.requests" to timerCount("http.server.requests"),
        ),
      deliveries = data.deliveryStatusCounts().associate { it.status to it.count },
      deliveryTrend = data.deliveryTrendSince(now().minusHours(24)),
      checkedAt = now(),
    )
  }

  private fun component(code: String, name: String, healthPath: String) =
    InfrastructureComponentResponse(
      code = code,
      name = name,
      status =
        runCatching { health.healthForPath(healthPath)?.status?.code ?: "UNKNOWN" }
          .getOrDefault("UNKNOWN"),
      connection = "Managed by deployment configuration",
    )

  private fun meter(name: String): Double =
    meters.find(name).gauges().sumOf { it.value().takeIf(Double::isFinite) ?: 0.0 }

  private fun timerCount(name: String): Double =
    meters.find(name).timers().sumOf { it.count().toDouble() }

  private fun now(): OffsetDateTime = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
}

@RestController
@RequestMapping("/api/admin/operations")
@Authenticated
@InstanceScoped
@SessionSecured
@StandardErrorResponses
@Tag(name = "Instance Operations", description = "Instance health and operational metrics.")
class InstanceOperationsController(private val operations: InstanceOperationsService) {
  @GetMapping
  @Authorize(action = "operations.read", resource = "operations")
  @Operation(
    summary = "Get instance operational snapshot",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Operational snapshot",
          useReturnTypeSchema = true,
        )
      ],
  )
  fun get(instanceContext: InstanceRequestContext): InstanceOperationsResponse =
    operations.snapshot(instanceContext.instance, instanceContext.apiVersion.value)
}

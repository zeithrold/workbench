package ink.doa.workbench.worker.messaging

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

@ConfigurationProperties("workbench.debezium")
data class DebeziumProperties(
  val connectUrl: String = "http://localhost:8083",
  val connectorName: String = "workbench-outbox",
  val requestTimeout: Duration = Duration.ofSeconds(5),
) {
  init {
    require(connectUrl.isNotBlank()) { "workbench.debezium.connect-url must not be blank" }
    require(connectorName.isNotBlank()) { "workbench.debezium.connector-name must not be blank" }
    require(!requestTimeout.isNegative && !requestTimeout.isZero) {
      "workbench.debezium.request-timeout must be positive"
    }
  }
}

data class DebeziumConnectorStatus(val connectorState: String, val taskStates: List<String>)

fun interface DebeziumConnectorStatusClient {
  fun status(): DebeziumConnectorStatus
}

@Component
@ConditionalOnProperty(name = ["workbench.messaging.transport"], havingValue = "kafka")
class HttpDebeziumConnectorStatusClient
internal constructor(
  private val properties: DebeziumProperties,
  private val client: HttpClient,
) : DebeziumConnectorStatusClient {
  @Autowired
  constructor(
    properties: DebeziumProperties
  ) : this(
    properties,
    HttpClient.newBuilder().connectTimeout(properties.requestTimeout).build(),
  )

  override fun status(): DebeziumConnectorStatus {
    val connector =
      URLEncoder.encode(properties.connectorName, StandardCharsets.UTF_8).replace("+", "%20")
    val request =
      HttpRequest.newBuilder()
        .uri(URI.create("${properties.connectUrl.trimEnd('/')}/connectors/$connector/status"))
        .timeout(properties.requestTimeout)
        .GET()
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
      "Debezium Connect returned HTTP ${response.statusCode()} for ${properties.connectorName}"
    }
    return parseDebeziumConnectorStatus(response.body())
  }
}

internal fun parseDebeziumConnectorStatus(body: String): DebeziumConnectorStatus {
  val root = Json.parseToJsonElement(body).jsonObject
  val connectorState = root.getValue("connector").jsonObject.getValue("state").jsonPrimitive.content
  val taskStates =
    root.getValue("tasks").jsonArray.map { task ->
      task.jsonObject.getValue("state").jsonPrimitive.content
    }
  return DebeziumConnectorStatus(connectorState, taskStates)
}

@Component("debeziumConnector")
@ConditionalOnProperty(name = ["workbench.messaging.transport"], havingValue = "kafka")
class DebeziumConnectorHealthIndicator(private val client: DebeziumConnectorStatusClient) :
  HealthIndicator {
  override fun health(): Health =
    runCatching { client.status() }
      .fold(
        onSuccess = { status ->
          val running =
            status.connectorState == RUNNING &&
              status.taskStates.isNotEmpty() &&
              status.taskStates.all { it == RUNNING }
          val builder = if (running) Health.up() else Health.down()
          builder
            .withDetail("connectorState", status.connectorState)
            .withDetail("taskStates", status.taskStates)
            .build()
        },
        onFailure = { error -> Health.down(error).build() },
      )

  private companion object {
    const val RUNNING = "RUNNING"
  }
}

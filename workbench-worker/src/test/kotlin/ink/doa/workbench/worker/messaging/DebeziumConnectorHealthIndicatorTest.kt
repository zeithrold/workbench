package ink.doa.workbench.worker.messaging

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.springframework.boot.health.contributor.Status

class DebeziumConnectorHealthIndicatorTest :
  StringSpec({
    "status parser reads connector and task states" {
      val status =
        parseDebeziumConnectorStatus(
          """{"name":"workbench-outbox","connector":{"state":"RUNNING"},"tasks":[{"id":0,"state":"RUNNING"}]}"""
        )

      status.connectorState shouldBe "RUNNING"
      status.taskStates.shouldContainExactly("RUNNING")
    }

    "HTTP status client calls the configured connector endpoint" {
      val http = mockk<HttpClient>()
      val response = mockk<HttpResponse<String>>()
      val request = slot<HttpRequest>()
      every { response.statusCode() } returns 200
      every { response.body() } returns
        """{"connector":{"state":"RUNNING"},"tasks":[{"state":"RUNNING"}]}"""
      every { http.send(capture(request), any<HttpResponse.BodyHandler<String>>()) } returns
        response

      val status =
        HttpDebeziumConnectorStatusClient(
            DebeziumProperties(
              connectUrl = "http://connect:8083/",
              connectorName = "workbench outbox",
            ),
            http,
          )
          .status()

      status.connectorState shouldBe "RUNNING"
      request.captured.uri().toString() shouldBe
        "http://connect:8083/connectors/workbench%20outbox/status"
    }

    "HTTP status client rejects non-success responses" {
      val http = mockk<HttpClient>()
      val response = mockk<HttpResponse<String>>()
      every { response.statusCode() } returns 503
      every { http.send(any(), any<HttpResponse.BodyHandler<String>>()) } returns response

      io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
        HttpDebeziumConnectorStatusClient(DebeziumProperties(), http).status()
      }
    }

    "health is up only when connector and tasks are running" {
      val indicator = DebeziumConnectorHealthIndicator {
        DebeziumConnectorStatus("RUNNING", listOf("RUNNING"))
      }

      indicator.health().status shouldBe Status.UP
    }

    "health is down for stopped tasks and client failures" {
      DebeziumConnectorHealthIndicator {
          DebeziumConnectorStatus("RUNNING", listOf("FAILED"))
        }
        .health()
        .status shouldBe Status.DOWN
      DebeziumConnectorHealthIndicator { error("connect unavailable") }.health().status shouldBe
        Status.DOWN
    }

    "properties reject invalid connector configuration" {
      io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
        DebeziumProperties(connectorName = "", requestTimeout = Duration.ZERO)
      }
    }
  })

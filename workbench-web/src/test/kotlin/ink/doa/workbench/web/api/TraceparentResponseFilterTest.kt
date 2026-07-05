package ink.doa.workbench.web.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micrometer.tracing.Span
import io.micrometer.tracing.TraceContext
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class TraceparentResponseFilterTest :
  StringSpec({
    val tracer = mockk<Tracer>()
    val propagator = mockk<Propagator>()
    val currentSpan = mockk<Span>()
    val traceContext = mockk<TraceContext>()

    beforeTest {
      clearMocks(tracer, propagator, currentSpan, traceContext)
      every { propagator.inject(any<TraceContext>(), any(), any()) } just runs
      every { traceContext.traceId() } returns "abc123"
      every { traceContext.spanId() } returns "def456"
      every { traceContext.sampled() } returns true
    }

    "injects traceparent when a trace context is active" {
      val filter = TraceparentResponseFilter(tracer, propagator)
      val request = MockHttpServletRequest()
      val response = MockHttpServletResponse()
      every { tracer.currentSpan() } returns currentSpan
      every { currentSpan.context() } returns traceContext

      filter.doFilter(request, response, MockFilterChain())

      verify(exactly = 1) {
        propagator.inject(traceContext, response, any())
      }
      response.getHeader("traceparent") shouldBe "00-abc123-def456-01"
    }

    "skips injection when no trace context is active" {
      val filter = TraceparentResponseFilter(tracer, propagator)
      val request = MockHttpServletRequest()
      val response = MockHttpServletResponse()
      every { tracer.currentSpan() } returns null

      filter.doFilter(request, response, MockFilterChain())

      verify(exactly = 0) {
        propagator.inject(any<TraceContext>(), any(), any())
      }
    }

    "skips injection when response already contains traceparent" {
      val filter = TraceparentResponseFilter(tracer, propagator)
      val request = MockHttpServletRequest()
      val response =
        MockHttpServletResponse().apply {
          setHeader(
            TraceparentResponseFilter.TRACEPARENT_HEADER,
            "00-existing-trace-existing-span-01",
          )
        }
      every { tracer.currentSpan() } returns currentSpan
      every { currentSpan.context() } returns traceContext

      filter.doFilter(request, response, MockFilterChain())

      verify(exactly = 0) {
        propagator.inject(any<TraceContext>(), any(), any())
      }
      response.getHeader(TraceparentResponseFilter.TRACEPARENT_HEADER) shouldBe
        "00-existing-trace-existing-span-01"
    }

    "response setter writes injected headers on the servlet response" {
      val filter = TraceparentResponseFilter(tracer, propagator)
      val request = MockHttpServletRequest()
      val response = MockHttpServletResponse()
      every { tracer.currentSpan() } returns currentSpan
      every { currentSpan.context() } returns traceContext
      val setterSlot = slot<Propagator.Setter<HttpServletResponse>>()
      every {
        propagator.inject(traceContext, response, capture(setterSlot))
      } answers
        {
          setterSlot.captured.set(response, "traceparent", "00-abc-def-01")
        }

      filter.doFilter(request, response, MockFilterChain())

      response.getHeader("traceparent") shouldBe "00-abc-def-01"
    }

    "falls back to W3C traceparent when propagator inject writes nothing" {
      val filter = TraceparentResponseFilter(tracer, propagator)
      val request = MockHttpServletRequest()
      val response = MockHttpServletResponse()
      every { tracer.currentSpan() } returns currentSpan
      every { currentSpan.context() } returns traceContext
      every { traceContext.traceId() } returns "abc123"
      every { traceContext.spanId() } returns "def456"
      every { traceContext.sampled() } returns true

      filter.doFilter(request, response, MockFilterChain())

      response.getHeader("traceparent") shouldBe "00-abc123-def456-01"
    }
  })

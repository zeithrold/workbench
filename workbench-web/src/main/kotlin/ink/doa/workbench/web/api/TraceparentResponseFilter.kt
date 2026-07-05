package ink.doa.workbench.web.api

import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

class TraceparentResponseFilter(
  private val tracer: Tracer,
  private val propagator: Propagator,
) : OncePerRequestFilter() {
  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    if (!response.containsHeader(TRACEPARENT_HEADER)) {
      val traceContext = tracer.currentSpan()?.context()
      if (traceContext != null) {
        propagator.inject(traceContext, response, RESPONSE_HEADER_SETTER)
        if (!response.containsHeader(TRACEPARENT_HEADER)) {
          val flags = if (traceContext.sampled() == true) "01" else "00"
          response.setHeader(
            TRACEPARENT_HEADER,
            "00-${traceContext.traceId()}-${traceContext.spanId()}-$flags",
          )
        }
      }
    }
    filterChain.doFilter(request, response)
  }

  companion object {
    const val TRACEPARENT_HEADER = "traceparent"

    val RESPONSE_HEADER_SETTER =
      Propagator.Setter<HttpServletResponse> { carrier, key, value ->
        carrier?.setHeader(key, value)
      }
  }
}

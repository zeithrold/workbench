package one.ztd.workbench.web.api.http

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import jakarta.servlet.http.Cookie
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import one.ztd.workbench.web.identity.LoginResponse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class SessionCookieWriterTest {
  private val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
  private val writer = SessionCookieWriter(clock)

  @Test
  fun `login response sets session cookie`() {
    val response =
      LoginResponse(
        user =
          UserSummary(
            id = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ0"),
            displayName = "Ada",
            primaryEmail = "ada@example.test",
          ),
        sessionExpiresAt = OffsetDateTime.parse("2026-07-04T12:00:00Z"),
        bearerToken = null,
      )

    val entity = writer.loginResponse(response, "session-secret")

    entity.headers.getFirst(HttpHeaders.SET_COOKIE).shouldContain(WORKBENCH_SESSION_COOKIE_NAME)
    entity.headers.getFirst(HttpHeaders.SET_COOKIE).shouldContain("session-secret")
    entity.body shouldBe response
  }

  @Test
  fun `logout response expires session cookie`() {
    val entity = writer.logoutResponse()

    entity.headers.getFirst(HttpHeaders.SET_COOKIE).shouldContain(WORKBENCH_SESSION_COOKIE_NAME)
    entity.headers.getFirst(HttpHeaders.SET_COOKIE).shouldContain("Max-Age=0")
  }

  @Test
  fun `created with session sets cookie on created response`() {
    val entity =
      writer.createdWithSession(
        body = mapOf("id" to "ten_abc"),
        sessionSecret = "bootstrap-secret",
        sessionExpiresAt = OffsetDateTime.parse("2026-07-04T12:00:00Z"),
      )

    entity.statusCode.value() shouldBe 201
    entity.headers.getFirst(HttpHeaders.SET_COOKIE).shouldContain("bootstrap-secret")
  }

  @Test
  fun `session cookie can be non-secure for local http profiles`() {
    val response = SessionCookieWriter(clock, secure = false).logoutResponse()

    response.headers.getFirst(HttpHeaders.SET_COOKIE).shouldNotContain("Secure")
  }
}

class HttpServletRequestExtensionsTest {
  @Test
  fun `session cookie value reads workbench session`() {
    val request =
      MockHttpServletRequest().apply {
        setCookies(Cookie(WORKBENCH_SESSION_COOKIE_NAME, "session-secret"))
      }

    request.sessionCookieValue() shouldBe "session-secret"
  }

  @Test
  fun `bearer token value parses authorization header`() {
    val request =
      MockHttpServletRequest().apply {
        addHeader(HttpHeaders.AUTHORIZATION, "Bearer opaque-token")
      }

    request.bearerTokenValue() shouldBe "opaque-token"
  }

  @Test
  fun `default redirect uri uses request host`() {
    val request =
      MockHttpServletRequest().apply {
        scheme = "https"
        serverName = "api.example.com"
        serverPort = 443
      }

    request.defaultRedirectUri("/api/auth/oauth2/callback") shouldBe
      "https://api.example.com/api/auth/oauth2/callback"
  }
}

class HttpClientContextTest {
  @Test
  fun `from captures client metadata`() {
    val request =
      MockHttpServletRequest().apply {
        remoteAddr = "203.0.113.10"
        addHeader(HttpHeaders.USER_AGENT, "WorkbenchTest/1.0")
        scheme = "https"
        serverName = "api.example.com"
        serverPort = 443
      }

    val context = HttpClientContext.from(request)

    context.ipAddress shouldBe "203.0.113.10"
    context.userAgent shouldBe "WorkbenchTest/1.0"
    context.requestHost?.scheme shouldBe "https"
    context.requestHost?.host shouldBe "api.example.com"
  }

  @Test
  fun `to service context maps network fields`() {
    val context =
      HttpClientContext(
        ipAddress = "203.0.113.10",
        userAgent = "WorkbenchTest/1.0",
        requestHost = null,
      )

    val service = context.toServiceContext()

    service.ipAddress shouldBe "203.0.113.10"
    service.userAgent shouldBe "WorkbenchTest/1.0"
  }
}

class ApiVersionFilterTest {
  private val filter = one.ztd.workbench.web.api.ApiVersionFilter()

  @Test
  fun `filter echoes requested api version`() {
    val request =
      MockHttpServletRequest().apply {
        addHeader(one.ztd.workbench.web.api.context.ApiVersion.HeaderName, "2026-07-01")
      }
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, MockFilterChain())

    response.getHeader(one.ztd.workbench.web.api.context.ApiVersion.HeaderName) shouldBe
      "2026-07-01"
    request.getAttribute(one.ztd.workbench.web.api.context.ApiVersion::class.java.name) shouldBe
      one.ztd.workbench.web.api.context.ApiVersion("2026-07-01")
  }
}

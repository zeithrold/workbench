package ink.doa.workbench.security

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
import org.springframework.mock.web.MockHttpServletRequest

class CrossSiteSessionRequestMatcherTest :
  StringSpec({
    "protects an unsafe cross-site request authenticated by session cookie" {
      request("POST", site = "cross-site", session = true).isProtected() shouldBe true
    }

    "does not require CSRF for safe cross-site requests" {
      request("GET", site = "cross-site", session = true).isProtected() shouldBe false
    }

    "does not require CSRF for bearer requests without a session cookie" {
      request("POST", site = "cross-site", session = false).isProtected() shouldBe false
    }

    "does not require CSRF for same-origin session requests" {
      request("DELETE", site = "same-origin", session = true).isProtected() shouldBe false
    }
  })

private fun request(method: String, site: String, session: Boolean): MockHttpServletRequest =
  MockHttpServletRequest().apply {
    this.method = method
    addHeader("Sec-Fetch-Site", site)
    if (session) setCookies(Cookie(WORKBENCH_SESSION_COOKIE_NAME, "session-secret"))
  }

private fun MockHttpServletRequest.isProtected(): Boolean =
  CrossSiteSessionRequestMatcher.matches(this)

package one.ztd.workbench.web.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement

private fun standardErrorResponseCodes(): Set<String> {
  val annotation = StandardErrorResponses::class.java.getAnnotation(ApiResponses::class.java)!!
  return annotation.value.map { it.responseCode }.toSet()
}

class OpenApiAnnotationsTest :
  StringSpec({
    "standard error responses annotation exposes shared api responses" {
      standardErrorResponseCodes() shouldBe setOf("400", "401", "403", "404", "409")
    }

    "session secured annotation requires session auth" {
      val annotation = SessionSecured::class.java.getAnnotation(SecurityRequirement::class.java)

      annotation shouldNotBe null
      annotation!!.name shouldBe "SessionAuth"
    }
  })

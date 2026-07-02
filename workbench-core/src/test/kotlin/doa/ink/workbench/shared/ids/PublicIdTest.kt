package doa.ink.workbench.shared.ids

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll

class PublicIdTest :
  StringSpec({
    "new ids include the requested prefix" {
      PublicId.new("iss").value.startsWith("iss_") shouldBe true
    }

    "valid public ids roundtrip" {
      val id = PublicId.new("usr")
      PublicId(id.value) shouldBe id
    }

    "invalid generated-like strings are rejected" {
      checkAll(Arb.stringPattern("[a-z]{2}_[A-Z0-9]{5}")) { candidate ->
        runCatching { PublicId(candidate) }.exceptionOrNull() shouldNotBe null
      }
    }
  })

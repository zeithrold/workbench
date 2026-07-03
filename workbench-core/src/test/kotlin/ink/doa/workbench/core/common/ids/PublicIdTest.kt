package ink.doa.workbench.core.common.ids

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PublicIdTest :
  StringSpec({
    "new ids include the requested prefix" {
      PublicId.new("iss").value.startsWith("iss_") shouldBe true
    }

    "valid public ids roundtrip" {
      val id = PublicId.new("usr")
      PublicId(id.value) shouldBe id
    }
  })

package one.ztd.workbench.kernel.common.ids

import io.kotest.assertions.throwables.shouldThrow
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

    "new rejects invalid prefix length" {
      shouldThrow<IllegalArgumentException> { PublicId.new("AB") }
      shouldThrow<IllegalArgumentException> { PublicId.new("user") }
      shouldThrow<IllegalArgumentException> { PublicId.new("USR") }
    }

    "constructor rejects malformed values" {
      shouldThrow<IllegalArgumentException> { PublicId("bad") }
      shouldThrow<IllegalArgumentException> { PublicId("usr_invalidsuffix") }
      shouldThrow<IllegalArgumentException> { PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ!") }
    }
  })

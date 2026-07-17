package one.ztd.workbench.kernel.common.ids

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll

@Tags("fuzz")
class PublicIdFuzzTest :
  StringSpec({
    "invalid generated-like strings are rejected" {
      checkAll(Arb.stringPattern("[a-z]{2}_[A-Z0-9]{5}")) { candidate ->
        runCatching { PublicId(candidate) }.exceptionOrNull() shouldNotBe null
      }
    }
  })

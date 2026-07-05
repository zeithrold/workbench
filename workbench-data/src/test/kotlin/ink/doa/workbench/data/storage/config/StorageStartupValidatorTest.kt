package ink.doa.workbench.data.storage.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec

class StorageStartupValidatorTest :
  StringSpec({
    "requires storage endpoint to be configured" {
      shouldThrow<IllegalArgumentException> {
        StorageStartupValidator(StorageProperties(endpoint = "")).afterPropertiesSet()
      }
    }
  })

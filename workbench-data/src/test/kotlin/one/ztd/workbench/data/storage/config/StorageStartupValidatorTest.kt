package one.ztd.workbench.data.storage.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec

class StorageStartupValidatorTest :
  StringSpec({
    "requires storage endpoint to be configured" {
      shouldThrow<IllegalArgumentException> {
        StorageStartupValidator(
            StorageProperties(
              endpoint = "",
              accessKey = "",
              secretKey = "",
              bucket = "workbench-attachments",
              region = "us-east-1",
              maxFileSize = org.springframework.util.unit.DataSize.ofMegabytes(25),
              autoCreateBucket = false,
              pathStyleAccess = true,
              presignUploadTtl = java.time.Duration.ofMinutes(15),
              presignDownloadTtl = java.time.Duration.ofMinutes(15),
            )
          )
          .afterPropertiesSet()
      }
    }
  })

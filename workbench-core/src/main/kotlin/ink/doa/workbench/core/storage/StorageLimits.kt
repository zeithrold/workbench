package ink.doa.workbench.core.storage

import java.time.Duration

data class StorageLimits(
  val maxFileSizeBytes: Long,
  val presignUploadTtl: Duration,
)

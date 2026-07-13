package ink.doa.workbench.kernel.storage

import java.time.Duration

data class StorageLimits(
  val maxFileSizeBytes: Long,
  val presignUploadTtl: Duration,
)

package ink.doa.workbench.data.storage

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize

@ConfigurationProperties(prefix = "workbench.storage")
data class StorageProperties(
  val endpoint: String = "",
  val accessKey: String = "",
  val secretKey: String = "",
  val bucket: String = "workbench-attachments",
  val region: String = "us-east-1",
  val maxFileSize: DataSize = DataSize.ofMegabytes(25),
  val autoCreateBucket: Boolean = false,
  /**
   * When true, bucket name is in the URL path (`endpoint/bucket/key`); when false, virtual-hosted
   * style (`bucket.endpoint/key`).
   */
  val pathStyleAccess: Boolean = true,
  val presignUploadTtl: Duration = Duration.ofMinutes(15),
  val presignDownloadTtl: Duration = Duration.ofMinutes(15),
)

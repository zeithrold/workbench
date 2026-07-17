package one.ztd.workbench.data.storage.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.util.unit.DataSize

/** S3-compatible object storage settings for attachment uploads and downloads. */
@ConfigurationProperties(prefix = "workbench.storage")
data class StorageProperties
@ConstructorBinding
constructor(
  /** Base URL of the S3-compatible endpoint (for example, MinIO or AWS S3). */
  @DefaultValue("") val endpoint: String,
  /** Access key for authenticating with the storage endpoint. */
  @DefaultValue("") val accessKey: String,
  /** Secret key for authenticating with the storage endpoint. */
  @DefaultValue("") val secretKey: String,
  /** Bucket used to store attachment objects. */
  @DefaultValue("workbench-attachments") val bucket: String,
  /** AWS region reported to the S3 client. */
  @DefaultValue("us-east-1") val region: String,
  /** Maximum allowed upload size for a single attachment. */
  @DefaultValue("25MB") val maxFileSize: DataSize,
  /** When true, create the bucket at startup if it does not already exist. */
  @DefaultValue("false") val autoCreateBucket: Boolean,
  /**
   * When true, bucket name is in the URL path (`endpoint/bucket/key`); when false, virtual-hosted
   * style (`bucket.endpoint/key`).
   */
  @DefaultValue("true") val pathStyleAccess: Boolean,
  /** Time-to-live for presigned upload URLs. */
  @DefaultValue("15m") val presignUploadTtl: Duration,
  /** Time-to-live for presigned download URLs. */
  @DefaultValue("15m") val presignDownloadTtl: Duration,
)

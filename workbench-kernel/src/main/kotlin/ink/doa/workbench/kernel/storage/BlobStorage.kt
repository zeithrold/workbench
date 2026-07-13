package ink.doa.workbench.kernel.storage

import java.time.OffsetDateTime

data class PresignedBlobRequest(
  val url: String,
  val method: String,
  val expiresAt: OffsetDateTime,
  val headers: Map<String, String> = emptyMap(),
)

data class BlobObjectHead(
  val contentType: String?,
  val contentLength: Long,
  val etag: String?,
)

interface BlobStorage {
  suspend fun presignPut(
    key: String,
    contentType: String?,
    contentLength: Long,
  ): PresignedBlobRequest

  suspend fun presignGet(key: String): PresignedBlobRequest

  suspend fun head(key: String): BlobObjectHead

  suspend fun delete(key: String)
}

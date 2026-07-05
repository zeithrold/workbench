package ink.doa.workbench.data.storage

import ink.doa.workbench.core.storage.BlobObjectHead
import ink.doa.workbench.core.storage.BlobStorage
import ink.doa.workbench.core.storage.BlobStorageObjectNotFoundException
import ink.doa.workbench.core.storage.PresignedBlobRequest
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

class InMemoryBlobStorage : BlobStorage {
  private val objects = ConcurrentHashMap<String, StoredObject>()

  override suspend fun presignPut(
    key: String,
    contentType: String?,
    contentLength: Long,
  ): PresignedBlobRequest {
    val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15)
    val headers = buildMap {
      contentType?.let { put("Content-Type", it) }
    }
    return PresignedBlobRequest(
      url = "inmemory://put/$key",
      method = "PUT",
      expiresAt = expiresAt,
      headers = headers,
    )
  }

  override suspend fun presignGet(key: String): PresignedBlobRequest {
    if (!objects.containsKey(key)) {
      throw BlobStorageObjectNotFoundException(key)
    }
    return PresignedBlobRequest(
      url = "inmemory://get/$key",
      method = "GET",
      expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15),
    )
  }

  override suspend fun head(key: String): BlobObjectHead =
    objects[key]?.let { stored ->
      BlobObjectHead(
        contentType = stored.contentType,
        contentLength = stored.bytes.size.toLong(),
        etag = stored.etag,
      )
    } ?: throw BlobStorageObjectNotFoundException(key)

  override suspend fun delete(key: String) {
    objects.remove(key)
  }

  fun putObject(key: String, contentType: String?, bytes: ByteArray) {
    objects[key] =
      StoredObject(
        contentType = contentType,
        bytes = bytes.copyOf(),
        etag = sha256(bytes),
      )
  }

  fun getObject(key: String): StoredObject =
    objects[key] ?: throw BlobStorageObjectNotFoundException(key)

  private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { byte -> "%02x".format(byte) }
  }

  data class StoredObject(
    val contentType: String?,
    val bytes: ByteArray,
    val etag: String,
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is StoredObject) return false
      return contentType == other.contentType &&
        bytes.contentEquals(other.bytes) &&
        etag == other.etag
    }

    override fun hashCode(): Int {
      var result = contentType?.hashCode() ?: 0
      result = 31 * result + bytes.contentHashCode()
      result = 31 * result + etag.hashCode()
      return result
    }
  }
}

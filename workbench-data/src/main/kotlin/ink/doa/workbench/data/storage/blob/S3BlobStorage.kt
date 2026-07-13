package ink.doa.workbench.data.storage.blob

import ink.doa.workbench.data.storage.config.StorageProperties
import ink.doa.workbench.kernel.storage.BlobObjectHead
import ink.doa.workbench.kernel.storage.BlobStorage
import ink.doa.workbench.kernel.storage.BlobStorageObjectNotFoundException
import ink.doa.workbench.kernel.storage.PresignedBlobRequest
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

class S3BlobStorage(
  private val s3Client: S3Client,
  private val s3Presigner: S3Presigner,
  private val properties: StorageProperties,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BlobStorage {
  init {
    if (properties.autoCreateBucket) {
      ensureBucketExists()
    }
  }

  override suspend fun presignPut(
    key: String,
    contentType: String?,
    contentLength: Long,
  ): PresignedBlobRequest =
    withContext(ioDispatcher) {
      val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(properties.presignUploadTtl)
      val putObjectRequest =
        PutObjectRequest.builder()
          .bucket(properties.bucket)
          .key(key)
          .contentLength(contentLength)
          .apply { contentType?.let { contentType(it) } }
          .build()
      val presigned =
        s3Presigner.presignPutObject(
          PutObjectPresignRequest.builder()
            .signatureDuration(properties.presignUploadTtl)
            .putObjectRequest(putObjectRequest)
            .build()
        )
      val headers = buildMap {
        contentType?.let { put("Content-Type", it) }
      }
      PresignedBlobRequest(
        url = presigned.url().toString(),
        method = "PUT",
        expiresAt = expiresAt,
        headers = headers,
      )
    }

  override suspend fun presignGet(key: String): PresignedBlobRequest =
    withContext(ioDispatcher) {
      val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(properties.presignDownloadTtl)
      val getObjectRequest = GetObjectRequest.builder().bucket(properties.bucket).key(key).build()
      val presigned =
        s3Presigner.presignGetObject(
          GetObjectPresignRequest.builder()
            .signatureDuration(properties.presignDownloadTtl)
            .getObjectRequest(getObjectRequest)
            .build()
        )
      PresignedBlobRequest(
        url = presigned.url().toString(),
        method = "GET",
        expiresAt = expiresAt,
      )
    }

  override suspend fun head(key: String): BlobObjectHead =
    withContext(ioDispatcher) {
      try {
        val response =
          s3Client.headObject(
            HeadObjectRequest.builder().bucket(properties.bucket).key(key).build()
          )
        BlobObjectHead(
          contentType = response.contentType(),
          contentLength = response.contentLength(),
          etag = response.eTag(),
        )
      } catch (_: NoSuchKeyException) {
        throw BlobStorageObjectNotFoundException(key)
      }
    }

  override suspend fun delete(key: String): Unit =
    withContext(ioDispatcher) {
      s3Client.deleteObject(
        DeleteObjectRequest.builder().bucket(properties.bucket).key(key).build()
      )
      return@withContext
    }

  private fun ensureBucketExists() {
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket).build())
    } catch (_: NoSuchBucketException) {
      s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket).build())
    } catch (exception: S3Exception) {
      if (exception.statusCode() == 404) {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket).build())
      } else {
        throw exception
      }
    }
  }

  companion object {
    fun createClient(properties: StorageProperties): S3Client =
      S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint))
        .region(Region.of(properties.region))
        .credentialsProvider(credentialsProvider(properties))
        .forcePathStyle(properties.pathStyleAccess)
        .build()

    fun createPresigner(properties: StorageProperties): S3Presigner =
      S3Presigner.builder()
        .endpointOverride(URI.create(properties.endpoint))
        .region(Region.of(properties.region))
        .credentialsProvider(credentialsProvider(properties))
        .serviceConfiguration(
          S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess).build()
        )
        .build()

    private fun credentialsProvider(properties: StorageProperties) =
      StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
      )
  }
}

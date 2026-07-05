package ink.doa.workbench.data.storage.config

import ink.doa.workbench.core.storage.BlobStorage
import ink.doa.workbench.core.storage.StorageLimits
import ink.doa.workbench.data.storage.blob.S3BlobStorage
import kotlinx.coroutines.CoroutineDispatcher
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
@EnableConfigurationProperties(StorageProperties::class)
class StorageConfiguration {
  @Bean
  fun storageLimits(properties: StorageProperties): StorageLimits =
    StorageLimits(
      maxFileSizeBytes = properties.maxFileSize.toBytes(),
      presignUploadTtl = properties.presignUploadTtl,
    )

  @Bean
  fun storageStartupValidator(properties: StorageProperties): StorageStartupValidator =
    StorageStartupValidator(properties)

  @Bean
  fun s3Client(properties: StorageProperties): S3Client = S3BlobStorage.createClient(properties)

  @Bean
  fun s3Presigner(properties: StorageProperties): S3Presigner =
    S3BlobStorage.createPresigner(properties)

  @Bean
  @ConditionalOnMissingBean(BlobStorage::class)
  fun s3BlobStorage(
    s3Client: S3Client,
    s3Presigner: S3Presigner,
    properties: StorageProperties,
    ioDispatcher: CoroutineDispatcher,
  ): BlobStorage = S3BlobStorage(s3Client, s3Presigner, properties, ioDispatcher)
}

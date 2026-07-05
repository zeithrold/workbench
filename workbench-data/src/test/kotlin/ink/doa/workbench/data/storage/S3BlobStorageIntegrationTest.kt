package ink.doa.workbench.data.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.springframework.util.unit.DataSize
import org.testcontainers.containers.MinIOContainer

@Tag("integration")
class S3BlobStorageIntegrationTest :
  StringSpec({
    "presign put head get and delete objects in minio" {
      MinIOContainer("minio/minio:RELEASE.2025-04-22T22-12-26Z")
        .withUserName("workbench")
        .withPassword("workbench")
        .apply { start() }
        .use { minio ->
          val properties =
            StorageProperties(
              endpoint = minio.s3URL,
              accessKey = minio.userName,
              secretKey = minio.password,
              bucket = "workbench-test",
              region = "us-east-1",
              maxFileSize = DataSize.ofMegabytes(25),
              autoCreateBucket = true,
            )
          val client = S3BlobStorage.createClient(properties)
          val presigner = S3BlobStorage.createPresigner(properties)
          val storage = S3BlobStorage(client, presigner, properties)
          val key = "tenant/issue/attachment/readme.txt"
          val bytes = "hello attachments".toByteArray()

          runBlocking {
            val upload =
              storage.presignPut(
                key = key,
                contentType = "text/plain",
                contentLength = bytes.size.toLong(),
              )
            upload.method shouldBe "PUT"

            val httpClient = HttpClient.newHttpClient()
            val putRequest =
              HttpRequest.newBuilder()
                .uri(URI.create(upload.url))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .header("Content-Type", "text/plain")
                .build()
            val putResponse = httpClient.send(putRequest, HttpResponse.BodyHandlers.ofString())
            putResponse.statusCode() shouldBe 200

            storage.head(key).contentLength shouldBe bytes.size.toLong()

            val download = storage.presignGet(key)
            download.method shouldBe "GET"
            val getRequest = HttpRequest.newBuilder().uri(URI.create(download.url)).GET().build()
            val getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofByteArray())
            getResponse.statusCode() shouldBe 200
            getResponse.body() shouldBe bytes

            storage.delete(key)
          }
        }
    }
  })

package ink.doa.workbench.data.storage.blob

import ink.doa.workbench.core.storage.BlobStorageObjectNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InMemoryBlobStorageTest :
  StringSpec({
    val storage = InMemoryBlobStorage()

    "getObject returns stored bytes" {
      val key = "tenant/issue/read.txt"
      val bytes = "payload".toByteArray()
      storage.putObject(key, "text/plain", bytes)

      storage.getObject(key).bytes shouldBe bytes
    }

    "presign put without content type omits content type header" {
      val presigned = storage.presignPut("tenant/issue/raw.bin", null, 10)
      presigned.headers.containsKey("Content-Type") shouldBe false
    }

    "presign put returns upload url and headers" {
      val presigned =
        storage.presignPut(
          key = "tenant/issue/new.txt",
          contentType = "text/plain",
          contentLength = 4,
        )

      presigned.method shouldBe "PUT"
      presigned.url shouldBe "inmemory://put/tenant/issue/new.txt"
      presigned.headers["Content-Type"] shouldBe "text/plain"
    }

    "presign put and head reflect stored object metadata" {
      val key = "tenant/issue/file.txt"
      storage.putObject(key, "text/plain", "hello".toByteArray())

      val head = storage.head(key)
      head.contentType shouldBe "text/plain"
      head.contentLength shouldBe 5

      val presignedGet = storage.presignGet(key)
      presignedGet.method shouldBe "GET"
      presignedGet.url shouldBe "inmemory://get/$key"
    }

    "presign get fails for missing object" {
      shouldThrow<BlobStorageObjectNotFoundException> { storage.presignGet("missing") }
    }

    "delete removes object" {
      val key = "tenant/issue/delete-me.txt"
      storage.putObject(key, "text/plain", "bye".toByteArray())
      storage.delete(key)
      shouldThrow<BlobStorageObjectNotFoundException> { storage.head(key) }
    }
  })

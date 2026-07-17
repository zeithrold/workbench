package one.ztd.workbench.data.locking

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.redisson.api.RLock
import org.redisson.api.RedissonClient

class RedissonDistributedLockServiceTest :
  StringSpec({
    "withLock runs block and releases lock when held" {
      val lock = mockk<RLock>(relaxed = true)
      val redisson = mockk<RedissonClient> { every { getLock("tenant-setup") } returns lock }
      every { lock.tryLock(1000, 5000, TimeUnit.MILLISECONDS) } returns true
      every { lock.isHeldByCurrentThread } returns true

      val service = RedissonDistributedLockService(redisson)
      val result =
        service.withLock(
          name = "tenant-setup",
          wait = Duration.ofSeconds(1),
          lease = Duration.ofSeconds(5),
        ) {
          "done"
        }

      result shouldBe "done"
      verify { lock.unlock() }
    }

    "withLock throws when lock cannot be acquired" {
      val lock = mockk<RLock>()
      val redisson = mockk<RedissonClient> { every { getLock("busy") } returns lock }
      every { lock.tryLock(any(), any(), any()) } returns false

      val service = RedissonDistributedLockService(redisson)
      shouldThrow<IllegalArgumentException> {
        service.withLock("busy", Duration.ofMillis(10), Duration.ofMillis(10)) { "never" }
      }
    }

    "withLock skips unlock when current thread does not hold lock" {
      val lock = mockk<RLock>(relaxed = true)
      val redisson = mockk<RedissonClient> { every { getLock("shared") } returns lock }
      every { lock.tryLock(any(), any(), any()) } returns true
      every { lock.isHeldByCurrentThread } returns false

      val service = RedissonDistributedLockService(redisson)
      service.withLock("shared", Duration.ofMillis(10), Duration.ofMillis(10)) { "ok" } shouldBe
        "ok"
      verify(exactly = 0) { lock.unlock() }
    }
  })

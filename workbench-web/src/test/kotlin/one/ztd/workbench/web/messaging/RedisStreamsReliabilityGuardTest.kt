package one.ztd.workbench.web.messaging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.springframework.boot.ApplicationArguments

class RedisStreamsReliabilityGuardTest :
  StringSpec({
    "reliable Redis configuration is accepted" {
      val script = mockk<RScript>()
      val redisson = mockk<RedissonClient>()
      every { redisson.script } returns script
      every { script.eval<List<Any>>(any(), any(), any()) } returns listOf("yes", "noeviction")

      RedisStreamsReliabilityGuard(redisson).run(mockk<ApplicationArguments>())
    }

    "missing AOF or an eviction policy rejects startup" {
      val script = mockk<RScript>()
      val redisson = mockk<RedissonClient>()
      every { redisson.script } returns script
      every { script.eval<List<Any>>(any(), any(), any()) } returns listOf("no", "noeviction")
      shouldThrow<IllegalArgumentException> {
        RedisStreamsReliabilityGuard(redisson).run(mockk<ApplicationArguments>())
      }
      every { script.eval<List<Any>>(any(), any(), any()) } returns listOf("yes", "allkeys-lru")
      shouldThrow<IllegalArgumentException> {
        RedisStreamsReliabilityGuard(redisson).run(mockk<ApplicationArguments>())
      }
    }
  })

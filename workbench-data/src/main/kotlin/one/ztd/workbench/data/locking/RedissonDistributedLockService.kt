package one.ztd.workbench.data.locking

import java.time.Duration
import one.ztd.workbench.kernel.port.locking.DistributedLockService
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component

@Component
class RedissonDistributedLockService(private val redissonClient: RedissonClient) :
  DistributedLockService {
  override fun <T> withLock(name: String, wait: Duration, lease: Duration, block: () -> T): T {
    val lock = redissonClient.getLock(name)
    val acquired =
      lock.tryLock(wait.toMillis(), lease.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
    require(acquired) { "Could not acquire distributed lock: $name" }
    return try {
      block()
    } finally {
      if (lock.isHeldByCurrentThread) lock.unlock()
    }
  }
}

package one.ztd.workbench.kernel.port.locking

import java.time.Duration

interface DistributedLockService {
  fun <T> withLock(name: String, wait: Duration, lease: Duration, block: () -> T): T
}

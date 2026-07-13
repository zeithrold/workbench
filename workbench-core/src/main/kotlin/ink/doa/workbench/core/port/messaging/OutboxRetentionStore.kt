package ink.doa.workbench.core.port.messaging

import java.time.OffsetDateTime

interface OutboxRetentionStore {
  fun deleteExpiredTerminal(limit: Int, now: OffsetDateTime): Int
}

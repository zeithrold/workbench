package ink.doa.workbench.application.messaging

import java.time.OffsetDateTime

interface OutboxRetentionStore {
  fun deleteExpiredTerminal(limit: Int, now: OffsetDateTime): Int
}

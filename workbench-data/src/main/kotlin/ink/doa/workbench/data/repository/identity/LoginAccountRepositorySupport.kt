package ink.doa.workbench.data.repository.identity

import java.time.OffsetDateTime
import java.time.ZoneOffset

internal fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

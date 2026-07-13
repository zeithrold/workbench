package ink.doa.workbench.core.common.errors

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class WorkbenchErrorCodeAuthorizationMappingTest :
  StringSpec({
    "maps known authorization reason codes" {
      WorkbenchErrorCode.fromAuthorizationReason("grant_denied") shouldBe
        WorkbenchErrorCode.AUTH_PERMISSION_GRANT_DENIED
      WorkbenchErrorCode.fromAuthorizationReason("missing_membership") shouldBe
        WorkbenchErrorCode.AUTH_PERMISSION_MISSING_MEMBERSHIP
    }

    "falls back to generic permission denied for unknown reason codes" {
      WorkbenchErrorCode.fromAuthorizationReason("unknown_reason") shouldBe
        WorkbenchErrorCode.AUTH_PERMISSION_DENIED
    }

    "exposes outbox admin error codes" {
      WorkbenchErrorCode.OUTBOX_MESSAGE_NOT_FOUND.code shouldBe "outbox.message.not_found"
      WorkbenchErrorCode.OUTBOX_DELIVERY_REPLAY_NOT_ALLOWED.code shouldBe
        "outbox.delivery.replay.not_allowed"
    }
  })

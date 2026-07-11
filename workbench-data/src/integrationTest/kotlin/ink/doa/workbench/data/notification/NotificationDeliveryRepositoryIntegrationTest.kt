package ink.doa.workbench.data.notification

import ink.doa.workbench.data.support.seedWorkItemStack
import ink.doa.workbench.testsupport.postgres.MigrationSpec
import ink.doa.workbench.testsupport.postgres.WorkbenchPostgresTestSupport
import ink.doa.workbench.testsupport.postgres.jdbcTemplate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database

class NotificationDeliveryRepositoryIntegrationTest :
  StringSpec({
    "claims an eligible email delivery with its notification details" {
      WorkbenchPostgresTestSupport.openDatabase(MigrationSpec.Full).use { lease ->
        val jdbc = lease.jdbcTemplate()
        val stack = runBlocking { seedWorkItemStack(Database.connect(jdbc.dataSource!!)) }
        val notificationId = UUID.randomUUID()
        val deliveryId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val lockedUntil = now.plusMinutes(2)

        jdbc.update(
          """
          INSERT INTO notifications (
            id, api_id, recipient_user_id, tenant_id, project_id, source_event_id,
            notification_type, title, body
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """
            .trimIndent(),
          notificationId,
          "notification-test",
          stack.actorId,
          stack.tenantId,
          stack.projectId,
          "event-test",
          "work_item.updated",
          "Test subject",
          "Test body",
        )
        jdbc.update(
          """
          INSERT INTO notification_deliveries (
            id, notification_id, channel, status, next_attempt_at
          ) VALUES (?, ?, 'EMAIL', 'PENDING', ?)
          """
            .trimIndent(),
          deliveryId,
          notificationId,
          now.minusMinutes(1),
        )

        val delivery =
          NotificationDeliveryRepository(jdbc).claimEmails(1, now, lockedUntil).single()

        delivery.deliveryId shouldBe deliveryId
        delivery.notificationId shouldBe notificationId
        delivery.recipient shouldBe
          jdbc.queryForObject(
            "SELECT primary_email FROM users WHERE id = ?",
            String::class.java,
            stack.actorId,
          )
        delivery.subject shouldBe "Test subject"
        delivery.body shouldBe "Test body"
        delivery.attempts shouldBe 0
        jdbc.queryForObject(
          "SELECT status FROM notification_deliveries WHERE id = ?",
          String::class.java,
          deliveryId,
        ) shouldBe "RETRY"
        jdbc.queryForObject(
          "SELECT next_attempt_at FROM notification_deliveries WHERE id = ?",
          OffsetDateTime::class.java,
          deliveryId,
        ) shouldBe lockedUntil
      }
    }
  })

package ink.doa.workbench.notification

interface WorkItemNotificationEventStore {
  /** Claims an event and creates its notification in one database transaction. */
  suspend fun processIfUnprocessed(
    consumerName: String,
    eventId: String,
    command: CreateNotificationCommand?,
  ): NotificationRecord?
}

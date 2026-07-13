package ink.doa.workbench.notification

interface EmailSender {
  fun send(message: EmailMessage)
}

data class EmailMessage(
  val recipient: String,
  val subject: String,
  val body: String,
)

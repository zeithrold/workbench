package ink.doa.workbench.agile.workitem.stream

interface WorkItemEventRepository {
  suspend fun <T : Any> append(command: AppendWorkItemEventCommand<T>): WorkItemEventRecord
}

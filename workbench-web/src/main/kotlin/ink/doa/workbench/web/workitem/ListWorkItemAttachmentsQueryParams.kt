package ink.doa.workbench.web.workitem

import org.springframework.web.bind.annotation.RequestParam

data class ListWorkItemAttachmentsQueryParams(
  @RequestParam(required = false) val purpose: String? = null,
  @RequestParam(required = false) val commentId: String? = null,
  @RequestParam(defaultValue = "50") val limit: Int = 50,
  @RequestParam(defaultValue = "0") val offset: Long = 0,
)

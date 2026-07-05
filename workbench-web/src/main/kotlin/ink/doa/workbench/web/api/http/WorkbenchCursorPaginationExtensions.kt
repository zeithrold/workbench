package ink.doa.workbench.web.api.http

import ink.doa.workbench.core.common.pagination.WorkbenchCursor
import org.springframework.http.ResponseEntity

fun <T : Any> ResponseEntity.BodyBuilder.headersIfNext(
  nextCursor: WorkbenchCursor?,
  body: T,
): ResponseEntity<T> {
  if (nextCursor == null) {
    return ResponseEntity.ok(body)
  }
  return ResponseEntity.ok().header(WORKBENCH_NEXT_CURSOR_HEADER, nextCursor.encode()).body(body)
}

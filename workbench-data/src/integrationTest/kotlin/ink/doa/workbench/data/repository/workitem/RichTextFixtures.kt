package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.core.workitem.richtext.RichTextDocument
import ink.doa.workbench.core.workitem.richtext.RichTextProcessor

internal fun richText(value: String): RichTextDocument =
  requireNotNull(RichTextProcessor.fromPlainText(value))

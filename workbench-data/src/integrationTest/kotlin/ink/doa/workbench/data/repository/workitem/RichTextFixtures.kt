package ink.doa.workbench.data.repository.workitem

import ink.doa.workbench.agile.workitem.richtext.RichTextDocument
import ink.doa.workbench.agile.workitem.richtext.RichTextProcessor

internal fun richText(value: String): RichTextDocument =
  requireNotNull(RichTextProcessor.fromPlainText(value))

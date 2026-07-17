package one.ztd.workbench.data.repository.workitem

import one.ztd.workbench.agile.workitem.richtext.RichTextDocument
import one.ztd.workbench.agile.workitem.richtext.RichTextProcessor

internal fun richText(value: String): RichTextDocument =
  requireNotNull(RichTextProcessor.fromPlainText(value))

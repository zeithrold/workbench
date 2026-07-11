import type { JSONContent } from '@tiptap/core'

export type RichTextDocument = JSONContent

export interface RichTextEditorProps {
  value: RichTextDocument
  onChange: (value: RichTextDocument) => void
  editable?: boolean
  placeholder?: string
  ariaLabel?: string
  contentWidth?: 'full' | 'reading'
  class?: string
}

export const EMPTY_RICH_TEXT_DOCUMENT: RichTextDocument = {
  type: 'doc',
  content: [{ type: 'paragraph' }],
}

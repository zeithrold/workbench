import type { JSONContent } from '@tiptap/core'

export interface RichTextDocument {
  format: 'tiptap'
  schemaVersion: 1
  content: JSONContent
}

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
  format: 'tiptap',
  schemaVersion: 1,
  content: {
    type: 'doc',
    content: [{ type: 'paragraph' }],
  },
}

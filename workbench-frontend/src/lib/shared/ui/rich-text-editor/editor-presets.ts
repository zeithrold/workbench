import type { EditorCommandId } from './editor-commands.js'
import type { RichTextEditorPreset } from './types.js'

interface EditorPresetConfig {
  commandGroups: readonly (readonly EditorCommandId[])[]
  codeBlock: boolean
  compact: boolean
  headings: boolean
  horizontalRule: boolean
  slashCommands: boolean
}

export const DEFAULT_RICH_TEXT_EDITOR_PRESET: RichTextEditorPreset = 'document'

export const EDITOR_PRESETS = {
  document: {
    commandGroups: [
      ['paragraph', 'heading-1', 'heading-2', 'heading-3'],
      ['bullet-list', 'ordered-list', 'blockquote', 'code-block'],
      ['bold', 'italic', 'strike', 'inline-code'],
    ],
    codeBlock: true,
    compact: false,
    headings: true,
    horizontalRule: true,
    slashCommands: true,
  },
  comment: {
    commandGroups: [
      ['bold', 'italic', 'strike', 'inline-code'],
      ['bullet-list', 'ordered-list', 'blockquote'],
    ],
    codeBlock: false,
    compact: true,
    headings: false,
    horizontalRule: false,
    slashCommands: false,
  },
} as const satisfies Record<RichTextEditorPreset, EditorPresetConfig>

import type { Editor, Range } from '@tiptap/core'
import type { Component } from 'svelte'
import {
  Bold,
  Braces,
  CodeXml,
  Heading1,
  Heading2,
  Heading3,
  Italic,
  List,
  ListOrdered,
  Minus,
  Pilcrow,
  Quote,
  Strikethrough,
} from '@lucide/svelte'

export type EditorCommandId
  = | 'paragraph' | 'heading-1' | 'heading-2' | 'heading-3'
    | 'bullet-list' | 'ordered-list' | 'blockquote' | 'code-block' | 'horizontal-rule'
    | 'bold' | 'italic' | 'strike' | 'inline-code'

export interface EditorCommand {
  id: EditorCommandId
  label: string
  description: string
  keywords: string[]
  group: 'Basic blocks' | 'Lists' | 'Content blocks' | 'Formatting'
  icon: Component
  active: (editor: Editor) => boolean
  available: (editor: Editor) => boolean
  execute: (editor: Editor, range?: Range) => boolean
  slash: boolean
}

function run(editor: Editor, command: (chain: ReturnType<Editor['chain']>) => ReturnType<Editor['chain']>, range?: Range) {
  let chain = editor.chain().focus()
  if (range)
    chain = chain.deleteRange(range)
  return command(chain).run()
}

export const EDITOR_COMMANDS: EditorCommand[] = [
  { id: 'paragraph', label: 'Text', description: 'Plain text paragraph', keywords: ['paragraph', 'text'], group: 'Basic blocks', icon: Pilcrow, active: e => e.isActive('paragraph'), available: e => e.can().setParagraph(), execute: (e, r) => run(e, c => c.setParagraph(), r), slash: true },
  { id: 'heading-1', label: 'Heading 1', description: 'Large section heading', keywords: ['h1', 'heading'], group: 'Basic blocks', icon: Heading1, active: e => e.isActive('heading', { level: 1 }), available: e => e.can().toggleHeading({ level: 1 }), execute: (e, r) => run(e, c => c.toggleHeading({ level: 1 }), r), slash: true },
  { id: 'heading-2', label: 'Heading 2', description: 'Medium section heading', keywords: ['h2', 'heading'], group: 'Basic blocks', icon: Heading2, active: e => e.isActive('heading', { level: 2 }), available: e => e.can().toggleHeading({ level: 2 }), execute: (e, r) => run(e, c => c.toggleHeading({ level: 2 }), r), slash: true },
  { id: 'heading-3', label: 'Heading 3', description: 'Small section heading', keywords: ['h3', 'heading'], group: 'Basic blocks', icon: Heading3, active: e => e.isActive('heading', { level: 3 }), available: e => e.can().toggleHeading({ level: 3 }), execute: (e, r) => run(e, c => c.toggleHeading({ level: 3 }), r), slash: true },
  { id: 'bullet-list', label: 'Bulleted list', description: 'Create a bulleted list', keywords: ['ul', 'bullet', 'list'], group: 'Lists', icon: List, active: e => e.isActive('bulletList'), available: e => e.can().toggleBulletList(), execute: (e, r) => run(e, c => c.toggleBulletList(), r), slash: true },
  { id: 'ordered-list', label: 'Numbered list', description: 'Create a numbered list', keywords: ['ol', 'number', 'list'], group: 'Lists', icon: ListOrdered, active: e => e.isActive('orderedList'), available: e => e.can().toggleOrderedList(), execute: (e, r) => run(e, c => c.toggleOrderedList(), r), slash: true },
  { id: 'blockquote', label: 'Quote', description: 'Highlight quoted content', keywords: ['quote', 'blockquote'], group: 'Content blocks', icon: Quote, active: e => e.isActive('blockquote'), available: e => e.can().toggleBlockquote(), execute: (e, r) => run(e, c => c.toggleBlockquote(), r), slash: true },
  { id: 'code-block', label: 'Code block', description: 'Insert highlighted code', keywords: ['code', 'code block'], group: 'Content blocks', icon: CodeXml, active: e => e.isActive('codeBlock'), available: e => e.can().toggleCodeBlock(), execute: (e, r) => run(e, c => c.toggleCodeBlock({ language: 'plaintext' }), r), slash: true },
  { id: 'horizontal-rule', label: 'Divider', description: 'Insert a horizontal divider', keywords: ['divider', 'rule', 'horizontal rule'], group: 'Content blocks', icon: Minus, active: () => false, available: e => e.can().setHorizontalRule(), execute: (e, r) => run(e, c => c.setHorizontalRule(), r), slash: true },
  { id: 'bold', label: 'Bold', description: 'Toggle bold', keywords: ['bold', 'strong'], group: 'Formatting', icon: Bold, active: e => e.isActive('bold'), available: e => e.can().toggleBold(), execute: e => run(e, c => c.toggleBold()), slash: false },
  { id: 'italic', label: 'Italic', description: 'Toggle italic', keywords: ['italic', 'emphasis'], group: 'Formatting', icon: Italic, active: e => e.isActive('italic'), available: e => e.can().toggleItalic(), execute: e => run(e, c => c.toggleItalic()), slash: false },
  { id: 'strike', label: 'Strikethrough', description: 'Toggle strikethrough', keywords: ['strike', 'strikethrough'], group: 'Formatting', icon: Strikethrough, active: e => e.isActive('strike'), available: e => e.can().toggleStrike(), execute: e => run(e, c => c.toggleStrike()), slash: false },
  { id: 'inline-code', label: 'Inline code', description: 'Toggle inline code', keywords: ['inline code', 'code'], group: 'Formatting', icon: Braces, active: e => e.isActive('code'), available: e => e.can().toggleCode(), execute: e => run(e, c => c.toggleCode()), slash: false },
]

const COMMANDS_BY_ID = new Map(EDITOR_COMMANDS.map(command => [command.id, command]))

export function resolveCommandGroups(groups: readonly (readonly EditorCommandId[])[]): EditorCommand[][] {
  return groups.map(group => group.map((id) => {
    const command = COMMANDS_BY_ID.get(id)
    if (!command)
      throw new Error(`Unknown editor command: ${id}`)
    return command
  }))
}

export function slashCommands(query: string, editor: Editor) {
  const normalized = query.trim().toLocaleLowerCase()
  return EDITOR_COMMANDS.filter(command => command.slash && command.available(editor))
    .filter(command => !normalized || [command.id, command.label, command.description, ...command.keywords]
      .some(value => value.toLocaleLowerCase().includes(normalized)))
}

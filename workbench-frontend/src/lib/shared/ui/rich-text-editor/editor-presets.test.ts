import { describe, expect, it } from 'vitest'
import { resolveCommandGroups } from './editor-commands.js'
import { DEFAULT_RICH_TEXT_EDITOR_PRESET, EDITOR_PRESETS } from './editor-presets.js'

describe('rich text editor presets', () => {
  it('defaults to the complete document preset', () => {
    expect(DEFAULT_RICH_TEXT_EDITOR_PRESET).toBe('document')
    expect(EDITOR_PRESETS.document).toMatchObject({
      headings: true,
      horizontalRule: true,
      codeBlock: true,
      slashCommands: true,
      compact: false,
    })
    const ids = resolveCommandGroups(EDITOR_PRESETS.document.commandGroups).flat().map(command => command.id)
    expect(ids).toContain('heading-1')
    expect(ids).toContain('code-block')
  })

  it('limits comments to lightweight formatting commands', () => {
    expect(EDITOR_PRESETS.comment).toMatchObject({
      headings: false,
      horizontalRule: false,
      codeBlock: false,
      slashCommands: false,
      compact: true,
    })
    expect(resolveCommandGroups(EDITOR_PRESETS.comment.commandGroups).flat().map(command => command.id)).toEqual([
      'bold',
      'italic',
      'strike',
      'inline-code',
      'bullet-list',
      'ordered-list',
      'blockquote',
    ])
  })
})

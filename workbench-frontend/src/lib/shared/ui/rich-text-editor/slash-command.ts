import type { SuggestionProps } from '@tiptap/suggestion'
import type { EditorCommand } from './editor-commands.js'
import { Extension } from '@tiptap/core'
import Suggestion from '@tiptap/suggestion'
import { mount, unmount } from 'svelte'
import { slashCommands } from './editor-commands.js'
import SlashCommandMenu from './slash-command-menu.svelte'

interface SlashItem extends EditorCommand {}

export const SlashCommand = Extension.create({
  name: 'slashCommand',

  addProseMirrorPlugins() {
    return [
      Suggestion<SlashItem>({
        editor: this.editor,
        char: '/',
        startOfLine: true,
        allowSpaces: true,
        allow: ({ state, range }) => {
          const parent = state.doc.resolve(range.from).parent
          return parent.type.name === 'paragraph'
        },
        items: ({ query, editor }) => slashCommands(query, editor),
        command: ({ editor, range, props }) => props.execute(editor, range),
        render: () => {
          let component: ReturnType<typeof mount> | undefined
          let detach: (() => void) | undefined

          const menuState = (props: SuggestionProps<SlashItem>) => ({
            items: props.items,
            select: (item: SlashItem) => props.command(item),
          })

          return {
            onStart(props) {
              const target = document.createElement('div')
              component = mount(SlashCommandMenu, { target, props: menuState(props) })
              detach = props.mount(target)
            },
            onUpdate(props) {
              ;(component as { update?: (state: ReturnType<typeof menuState>) => void })?.update?.(menuState(props))
            },
            onKeyDown({ event }) {
              if (event.key === 'Escape')
                return false
              return (component as { onKeyDown?: (event: KeyboardEvent) => boolean })?.onKeyDown?.(event) ?? false
            },
            onExit() {
              detach?.()
              if (component)
                void unmount(component)
              component = undefined
              detach = undefined
            },
          }
        },
      }),
    ]
  },
})

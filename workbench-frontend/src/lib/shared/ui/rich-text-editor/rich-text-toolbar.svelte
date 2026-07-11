<script lang='ts'>
  import type { Editor } from '@tiptap/core'
  import type { EditorCommand } from './editor-commands.js'
  import { Button } from '$lib/components/ui/button'
  import { Separator } from '$lib/components/ui/separator'
  import { Redo2, Undo2 } from '@lucide/svelte'
  import LinkEditor from './link-editor.svelte'

  const { editor, revision, commandGroups, compact = false, disabled = false }: {
    editor: Editor
    revision: number
    commandGroups: EditorCommand[][]
    compact?: boolean
    disabled?: boolean
  } = $props()

  function isActive(item: EditorCommand) {
    void revision
    return item.active(editor)
  }

  function canUndo() {
    void revision
    return editor.can().chain().focus().undo().run()
  }

  function canRedo() {
    void revision
    return editor.can().chain().focus().redo().run()
  }
</script>

<div
  class={compact
    ? 'flex items-center gap-x-2 overflow-x-auto border-b border-border/60 bg-muted/15 px-2 py-1.5'
    : 'flex flex-wrap items-center gap-x-2 gap-y-1 border-b border-border/60 bg-muted/15 px-2 py-1.5'}
  role='toolbar'
  aria-label='Text formatting'
  data-revision={revision}
>
  {#each commandGroups as group, index (index)}
    <div class='flex items-center gap-0.5'>
      {#each group as item (item.id)}
        <Button
          variant='ghost'
          size='icon-sm'
          class={isActive(item) ? 'bg-accent text-accent-foreground shadow-xs' : 'text-muted-foreground hover:text-foreground'}
          aria-label={item.label}
          aria-pressed={isActive(item)}
          title={item.label}
          disabled={disabled || !item.available(editor)}
          onmousedown={event => event.preventDefault()}
          onclick={() => item.execute(editor)}
        >
          <item.icon class='size-3.5' />
        </Button>
      {/each}
    </div>
  {/each}
  <div class='flex items-center gap-0.5'>
    <LinkEditor {editor} {revision} {disabled} />
  </div>
  {#if !compact}
    <Separator orientation='vertical' class='hidden h-5 sm:block' />
    <div class='flex items-center gap-0.5'>
      <Button
        variant='ghost'
        size='icon-sm'
        class='text-muted-foreground hover:text-foreground'
        aria-label='Undo'
        title='Undo'
        disabled={disabled || !canUndo()}
        onmousedown={event => event.preventDefault()}
        onclick={() => editor.chain().focus().undo().run()}
      ><Undo2 class='size-3.5' /></Button>
      <Button
        variant='ghost'
        size='icon-sm'
        class='text-muted-foreground hover:text-foreground'
        aria-label='Redo'
        title='Redo'
        disabled={disabled || !canRedo()}
        onmousedown={event => event.preventDefault()}
        onclick={() => editor.chain().focus().redo().run()}
      ><Redo2 class='size-3.5' /></Button>
    </div>
  {/if}
</div>

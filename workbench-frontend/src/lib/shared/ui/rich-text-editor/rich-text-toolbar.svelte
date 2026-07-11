<script lang='ts'>
  import type { Editor } from '@tiptap/core'
  import { Button } from '$lib/components/ui/button'
  import { Separator } from '$lib/components/ui/separator'
  import { Redo2, Undo2 } from '@lucide/svelte'
  import { TOOLBAR_COMMANDS } from './editor-commands.js'
  import LinkEditor from './link-editor.svelte'

  const { editor, revision }: { editor: Editor, revision: number } = $props()

  function isActive(item: typeof TOOLBAR_COMMANDS[number]) {
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

<div class='flex flex-wrap items-center gap-x-2 gap-y-1 border-b border-border/60 bg-muted/15 px-2 py-1.5' role='toolbar' aria-label='Text formatting' data-revision={revision}>
  {#each [[0, 4], [4, 8], [8, 12]] as range (range[0])}
    <div class='flex items-center gap-0.5'>
      {#each TOOLBAR_COMMANDS.slice(range[0], range[1]) as item (item.id)}
        <Button
          variant='ghost'
          size='icon-sm'
          class={isActive(item) ? 'bg-accent text-accent-foreground shadow-xs' : 'text-muted-foreground hover:text-foreground'}
          aria-label={item.label}
          aria-pressed={isActive(item)}
          title={item.label}
          onmousedown={event => event.preventDefault()}
          onclick={() => item.execute(editor)}
        >
          <item.icon class='size-3.5' />
        </Button>
      {/each}
    </div>
  {/each}
  <div class='flex items-center gap-0.5'>
    <LinkEditor {editor} {revision} />
  </div>
  <Separator orientation='vertical' class='hidden h-5 sm:block' />
  <div class='flex items-center gap-0.5'>
    <Button
      variant='ghost'
      size='icon-sm'
      class='text-muted-foreground hover:text-foreground'
      aria-label='Undo'
      title='Undo'
      disabled={!canUndo()}
      onmousedown={event => event.preventDefault()}
      onclick={() => editor.chain().focus().undo().run()}
    ><Undo2 class='size-3.5' /></Button>
    <Button
      variant='ghost'
      size='icon-sm'
      class='text-muted-foreground hover:text-foreground'
      aria-label='Redo'
      title='Redo'
      disabled={!canRedo()}
      onmousedown={event => event.preventDefault()}
      onclick={() => editor.chain().focus().redo().run()}
    ><Redo2 class='size-3.5' /></Button>
  </div>
</div>

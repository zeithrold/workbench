<script lang='ts'>
  import type { Editor } from '@tiptap/core'
  import { Button } from '$lib/components/ui/button'
  import { Separator } from '$lib/components/ui/separator'
  import { m } from '$lib/paraglide/messages.js'
  import { createEditorCommands } from './editor-commands.js'
  import LinkEditor from './link-editor.svelte'

  let { editor, revision, ref = $bindable() }: {
    editor?: Editor
    revision: number
    ref?: HTMLDivElement
  } = $props()

  const commands = $derived(createEditorCommands().filter(command => command.group === 'Formatting'))

  function isActive(id: string) {
    void revision
    return editor?.isActive(id === 'inline-code' ? 'code' : id) ?? false
  }
</script>

<div
  bind:this={ref}
  class='flex items-center gap-0.5 rounded-md bg-popover p-1 text-popover-foreground shadow-md ring-1 ring-foreground/10'
  role='toolbar'
  aria-label={m.selection_formatting()}
>
  {#if editor}
    {#each commands as command (command.id)}
      <Button
        variant='ghost'
        size='icon-sm'
        class={isActive(command.id) ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground'}
        aria-label={command.label}
        aria-pressed={isActive(command.id)}
        title={command.label}
        disabled={!command.available(editor)}
        onmousedown={event => event.preventDefault()}
        onclick={() => command.execute(editor)}
      >
        <command.icon class='size-3.5' />
      </Button>
    {/each}
    <Separator orientation='vertical' class='mx-0.5 h-5' />
    <LinkEditor {editor} {revision} />
  {/if}
</div>

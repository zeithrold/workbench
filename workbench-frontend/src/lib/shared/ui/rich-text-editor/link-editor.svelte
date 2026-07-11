<script lang='ts'>
  import type { Editor } from '@tiptap/core'
  import { Button } from '$lib/components/ui/button'
  import { Input } from '$lib/components/ui/input'
  import * as Popover from '$lib/components/ui/popover'
  import { Link, Link2Off } from '@lucide/svelte'

  const { editor, revision, disabled = false }: { editor: Editor, revision: number, disabled?: boolean } = $props()

  let open = $state(false)
  let href = $state('')
  let hasManagedLink = $state(false)
  let selection = $state<{ from: number, to: number }>()

  function openEditor() {
    if (!hasManagedLink)
      selection = { from: editor.state.selection.from, to: editor.state.selection.to }
    href = editor.getAttributes('link').href ?? ''
    open = true
  }

  function saveLink(event: SubmitEvent) {
    event.preventDefault()
    const value = href.trim()
    if (value) {
      const chain = editor.chain().focus()
      if (selection)
        chain.setTextSelection(selection)
      chain.extendMarkRange('link').setLink({ href: value }).run()
      hasManagedLink = true
      open = false
    }
  }

  function removeLink() {
    const chain = editor.chain().focus()
    if (selection)
      chain.setTextSelection(selection)
    chain.extendMarkRange('link').unsetLink().run()
    href = ''
    hasManagedLink = false
    open = false
  }

  function isLinkActive() {
    void revision
    return editor.isActive('link')
  }
</script>

<Popover.Root bind:open>
  <Popover.Trigger>
    {#snippet child({ props })}
      <Button
        {...props}
        variant='ghost'
        size='icon-sm'
        aria-label='Edit link'
        aria-pressed={isLinkActive()}
        title='Edit link'
        {disabled}
        onmousedown={event => event.preventDefault()}
        onclick={openEditor}
      >
        <Link class='size-3.5' />
      </Button>
    {/snippet}
  </Popover.Trigger>
  <Popover.Content align='start' class='w-80 gap-3'>
    <form class='space-y-3' onsubmit={saveLink}>
      <label class='space-y-1.5 text-sm font-medium'>
        Link URL
        <Input bind:value={href} type='url' placeholder='https://example.com' aria-label='Link URL' />
      </label>
      <div class='flex justify-end gap-2'>
        {#if isLinkActive() || hasManagedLink}
          <Button type='button' variant='ghost' size='sm' onclick={removeLink}>
            <Link2Off class='size-3.5' />
            Remove
          </Button>
        {/if}
        <Button type='submit' size='sm' disabled={!href.trim()}>Apply</Button>
      </div>
    </form>
  </Popover.Content>
</Popover.Root>

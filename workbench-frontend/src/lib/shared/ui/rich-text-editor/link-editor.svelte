<script lang='ts'>
  import type { Editor } from '@tiptap/core'
  import { Button } from '$lib/components/ui/button'
  import { Input } from '$lib/components/ui/input'
  import { Link, Link2Off } from '@lucide/svelte'

  const { editor, revision, disabled = false }: { editor: Editor, revision: number, disabled?: boolean } = $props()

  let open = $state(false)
  let href = $state('')
  let hasManagedLink = $state(false)
  let selection = $state<{ from: number, to: number }>()
  let trigger = $state.raw<HTMLButtonElement | null>(null)
  let panel = $state.raw<HTMLDivElement | null>(null)

  function closeEditor() {
    if (panel?.matches(':popover-open'))
      panel.hidePopover()
  }

  function openEditor() {
    if (!hasManagedLink)
      selection = { from: editor.state.selection.from, to: editor.state.selection.to }
    href = editor.getAttributes('link').href ?? ''
    if (!trigger || !panel)
      return
    const triggerBounds = trigger.getBoundingClientRect()
    panel.style.left = `${Math.max(8, Math.min(triggerBounds.left, window.innerWidth - 328))}px`
    panel.style.top = `${triggerBounds.bottom + 4}px`
    panel.showPopover()
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
      closeEditor()
    }
  }

  function removeLink() {
    const chain = editor.chain().focus()
    if (selection)
      chain.setTextSelection(selection)
    chain.extendMarkRange('link').unsetLink().run()
    href = ''
    hasManagedLink = false
    closeEditor()
  }

  function isLinkActive() {
    void revision
    return editor.isActive('link')
  }

  function handleToggle(event: ToggleEvent) {
    open = event.newState === 'open'
  }
</script>

<Button
  bind:ref={trigger}
  variant='ghost'
  size='icon-sm'
  aria-label='Edit link'
  aria-pressed={isLinkActive()}
  aria-expanded={open}
  title='Edit link'
  {disabled}
  onmousedown={event => event.preventDefault()}
  onclick={openEditor}
>
  <Link class='size-3.5' />
</Button>

<!-- Native popover teardown is synchronous and cannot outlive the owning Tiptap view. -->
<div
  bind:this={panel}
  popover='auto'
  class='fixed z-50 m-0 w-80 rounded-md bg-popover p-4 text-sm text-popover-foreground shadow-md ring-1 ring-foreground/10 outline-hidden'
  ontoggle={handleToggle}
>
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
</div>

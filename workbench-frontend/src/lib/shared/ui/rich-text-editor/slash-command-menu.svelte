<script lang='ts'>
  import type { EditorCommand } from './editor-commands.js'
  import { untrack } from 'svelte'

  interface MenuState {
    items: EditorCommand[]
    select: (item: EditorCommand) => void
  }

  const initial: MenuState = $props()
  let items = $state(untrack(() => initial.items))
  let select = untrack(() => initial.select)
  let selected = $state(0)

  export function update(next: MenuState) {
    items = next.items
    select = next.select
    selected = Math.min(selected, Math.max(0, items.length - 1))
  }

  export function onKeyDown(event: KeyboardEvent) {
    if (event.isComposing)
      return false
    if (event.key === 'ArrowUp') {
      selected = (selected + items.length - 1) % items.length
      return true
    }
    if (event.key === 'ArrowDown') {
      selected = (selected + 1) % items.length
      return true
    }
    if (event.key === 'Enter' && items[selected]) {
      select(items[selected])
      return true
    }
    return false
  }
</script>

<div class='slash-menu' role='listbox' aria-label='Editor commands'>
  {#if items.length === 0}
    <div class='px-3 py-2 text-sm text-muted-foreground'>No matching commands</div>
  {:else}
    {#each items as item, index (item.id)}
      <button
        type='button'
        role='option'
        aria-selected={index === selected}
        class:active={index === selected}
        onmouseenter={() => selected = index}
        onmousedown={event => event.preventDefault()}
        onclick={() => select(item)}
      >
        <span class='slash-menu-icon'><item.icon class='size-3.5' /></span>
        <span class='min-w-0 text-left'>
          <span class='block text-sm font-medium'>{item.label}</span>
          <span class='block truncate text-xs text-muted-foreground'>{item.description}</span>
        </span>
      </button>
    {/each}
  {/if}
</div>

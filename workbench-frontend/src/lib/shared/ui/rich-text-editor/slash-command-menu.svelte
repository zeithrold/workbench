<script lang='ts'>
  import type { EditorCommand } from './editor-commands.js'
  import * as Command from '$lib/components/ui/command'
  import { ScrollArea } from '$lib/components/ui/scroll-area'
  import { tick, untrack } from 'svelte'

  interface MenuState {
    items: EditorCommand[]
    select: (item: EditorCommand) => void
  }

  const initial: MenuState = $props()
  let items = $state(untrack(() => initial.items))
  let select = untrack(() => initial.select)
  let selected = $state(untrack(() => initial.items[0]?.id ?? ''))
  let menu: HTMLDivElement

  function scrollToSelected() {
    void tick().then(() => {
      menu.querySelector<HTMLElement>('[data-command-item][data-selected]')
        ?.scrollIntoView({ block: 'nearest' })
    })
  }

  export function update(next: MenuState) {
    items = next.items
    select = next.select
    if (!items.some(item => item.id === selected))
      selected = items[0]?.id ?? ''
  }

  export function onKeyDown(event: KeyboardEvent) {
    if (event.isComposing)
      return false
    const selectedIndex = items.findIndex(item => item.id === selected)
    if (event.key === 'ArrowUp') {
      selected = items[(selectedIndex + items.length - 1) % items.length]?.id ?? ''
      scrollToSelected()
      return true
    }
    if (event.key === 'ArrowDown') {
      selected = items[(selectedIndex + 1) % items.length]?.id ?? ''
      scrollToSelected()
      return true
    }
    const selectedItem = items.find(item => item.id === selected)
    if (event.key === 'Enter' && selectedItem) {
      select(selectedItem)
      return true
    }
    return false
  }
</script>

<div bind:this={menu}>
  <Command.Root bind:value={selected} shouldFilter={false} loop class='w-76 rounded-md border p-1 shadow-lg' label='Editor commands'>
    <ScrollArea class='h-[min(22rem,60vh)]'>
      <Command.List class='max-h-none overflow-visible' aria-label='Editor commands'>
        {#if items.length === 0}
          <Command.Empty>No matching commands</Command.Empty>
        {:else}
          <Command.Group>
            {#each items as item (item.id)}
              <Command.Item
                value={item.id}
                class='w-full gap-2.5 py-2'
                onmousedown={event => event.preventDefault()}
                onSelect={() => select(item)}
              >
                <span class='grid size-7 shrink-0 place-items-center rounded-md border bg-background'>
                  <item.icon class='size-3.5' />
                </span>
                <span class='min-w-0 flex-1 text-left'>
                  <span class='flex min-w-0 items-center justify-between gap-3'>
                    <span class='truncate text-sm font-medium'>{item.label}</span>
                    <code class='shrink-0 rounded bg-muted px-1.5 py-0.5 text-[10px] font-normal text-muted-foreground'>/{item.id}</code>
                  </span>
                  <span class='block truncate text-xs text-muted-foreground'>{item.description}</span>
                </span>
              </Command.Item>
            {/each}
          </Command.Group>
        {/if}
      </Command.List>
    </ScrollArea>
  </Command.Root>
</div>

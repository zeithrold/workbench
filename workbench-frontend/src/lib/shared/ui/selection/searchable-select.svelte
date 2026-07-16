<script lang='ts'>
  import type { Snippet } from 'svelte'
  import type { SelectorOption } from './selector-model.js'
  import * as Command from '$lib/components/ui/command'
  import { ScrollArea } from '$lib/components/ui/scroll-area'
  import { m } from '$lib/paraglide/messages.js'
  import { cn } from '$lib/utils.js'
  import { ChevronDown, X } from '@lucide/svelte'
  import { tick } from 'svelte'
  import { dropdownInTransition } from './dropdown-transition.js'
  import SelectorLoading from './selector-loading.svelte'
  import { filterSelectorOptions, prioritizeSelectedOptions } from './selector-model.js'
  import SelectorOptionView from './selector-option.svelte'

  const {
    value,
    options,
    onValueChange,
    placeholder = m.select(),
    searchPlaceholder = m.search(),
    emptyText = m.no_options_found(),
    disabled = false,
    required = false,
    clearable = true,
    loading = false,
    onSearchChange,
    class: className,
    triggerContent,
    optionContent,
  }: {
    value?: string | null
    options: SelectorOption[]
    onValueChange: (value: string | null) => void
    placeholder?: string
    searchPlaceholder?: string
    emptyText?: string
    disabled?: boolean
    required?: boolean
    clearable?: boolean
    loading?: boolean
    onSearchChange?: (query: string) => void
    class?: string
    triggerContent?: Snippet<[SelectorOption]>
    optionContent?: Snippet<[SelectorOption]>
  } = $props()

  let open = $state(false)
  let query = $state('')
  let commandValue = $state('')
  let searchInput: HTMLInputElement | null = $state(null)
  let root: HTMLDivElement | null = $state(null)
  const selected = $derived(options.find(option => option.id === value))
  const filtered = $derived(prioritizeSelectedOptions(filterSelectorOptions(options, query), value ? [value] : []))
  const groups = $derived.by(() => {
    const result: Array<[string, SelectorOption[]]> = []
    for (const option of filtered) {
      const group = option.group ?? ''
      const existing = result.find(([name]) => name === group)
      if (existing)
        existing[1].push(option)
      else
        result.push([group, [option]])
    }
    return result
  })

  async function show() {
    if (disabled)
      return
    open = true
    commandValue = value ?? filtered[0]?.id ?? ''
    await tick()
    searchInput?.focus()
  }

  function close() {
    open = false
    query = ''
    commandValue = ''
    onSearchChange?.('')
  }

  function choose(id: string) {
    if (id === value) {
      close()
      return
    }
    close()
    onValueChange(id)
  }

  function clearSelection(event: MouseEvent) {
    event.stopPropagation()
    onValueChange(null)
  }

  function handleTriggerKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      close()
      return
    }
    if ((event.key === 'Delete' || event.key === 'Backspace') && selected && clearable && !required) {
      event.preventDefault()
      onValueChange(null)
    }
  }

  function updateQuery(event: Event) {
    query = (event.currentTarget as HTMLInputElement).value
    commandValue = filtered[0]?.id ?? ''
    onSearchChange?.(query)
  }

  function handleWindowClick(event: MouseEvent) {
    if (open && root && !root.contains(event.target as Node))
      close()
  }
</script>

<svelte:window onclick={handleWindowClick} />

<div bind:this={root} class={cn('relative w-full', className)} data-slot='searchable-select'>
  <button
    type='button'
    class='flex h-9 w-full items-center justify-between gap-2 rounded-md border border-input bg-background px-2.5 text-sm shadow-xs outline-none transition focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50'
    aria-haspopup='listbox'
    aria-expanded={open}
    data-required={required}
    {disabled}
    onclick={() => open ? close() : void show()}
    onkeydown={handleTriggerKeydown}
  >
    {#if selected}
      {#if triggerContent}{@render triggerContent(selected)}{:else}<SelectorOptionView option={selected} compact />{/if}
    {:else}<span class='truncate text-muted-foreground'>{placeholder}</span>{/if}
    <span class='ml-auto flex shrink-0 items-center'>
      {#if selected && clearable && !required}
        <span
          aria-label={m.clear_selection()}
          aria-hidden='true'
          class='rounded p-0.5 hover:bg-muted'
          onclick={clearSelection}
        ><X class='size-3.5' /></span>
      {/if}
      <ChevronDown class='size-4 text-muted-foreground' />
    </span>
  </button>

  {#if open}
    <div class='absolute z-50 mt-1 w-full min-w-64 origin-top rounded-md border bg-popover p-1 text-popover-foreground shadow-lg' in:dropdownInTransition>
      <Command.Root bind:value={commandValue} shouldFilter={false} loop class='rounded-md p-0' label={m.options()}>
        <Command.Input bind:ref={searchInput} value={query} oninput={updateQuery} placeholder={searchPlaceholder} />
        <ScrollArea class='h-52'>
          <Command.List class='max-h-none overflow-visible' aria-busy={loading}>
            {#if loading}
              <SelectorLoading />
            {:else if filtered.length === 0}
              <Command.Empty>{emptyText}</Command.Empty>
            {:else}
              {#each groups as [group, groupOptions] (group)}
                <Command.Group heading={group || undefined}>
                  {#each groupOptions as option (option.id)}
                    <Command.Item value={option.id} onSelect={() => choose(option.id)} class='w-full py-2' data-checked={option.id === value}>
                      {#if optionContent}{@render optionContent(option)}{:else}<SelectorOptionView {option} />{/if}
                    </Command.Item>
                  {/each}
                </Command.Group>
              {/each}
            {/if}
          </Command.List>
        </ScrollArea>
      </Command.Root>
    </div>
  {/if}
</div>

<script lang='ts'>
  import type { SelectorOption } from './selector-model.js'
  import { cn } from '$lib/utils.js'
  import { Check, ChevronDown, Search, X } from '@lucide/svelte'
  import { tick } from 'svelte'
  import { dropdownInTransition, dropdownOutTransition } from './dropdown-transition.js'
  import SelectorLoading from './selector-loading.svelte'
  import { filterSelectorOptions, prioritizeSelectedOptions } from './selector-model.js'
  import SelectorOptionView from './selector-option.svelte'

  const {
    value,
    options,
    onValueChange,
    placeholder = 'Select…',
    searchPlaceholder = 'Search…',
    emptyText = 'No options found',
    disabled = false,
    required = false,
    clearable = true,
    loading = false,
    onSearchChange,
    class: className,
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
  } = $props()

  let open = $state(false)
  let query = $state('')
  let activeIndex = $state(0)
  let searchInput: HTMLInputElement | null = $state(null)
  let root: HTMLDivElement | null = $state(null)
  const selected = $derived(options.find(option => option.id === value))
  const filtered = $derived(prioritizeSelectedOptions(filterSelectorOptions(options, query), value ? [value] : []))

  async function show() {
    if (disabled)
      return
    open = true
    activeIndex = Math.max(0, filtered.findIndex(option => option.id === value))
    await tick()
    searchInput?.focus()
  }

  function close() {
    open = false
    query = ''
    onSearchChange?.('')
  }

  function choose(id: string) {
    onValueChange(id)
    close()
  }

  function clearSelection(event: MouseEvent) {
    event.stopPropagation()
    onValueChange(null)
  }

  function handleTriggerKeydown(event: KeyboardEvent) {
    if ((event.key === 'Delete' || event.key === 'Backspace') && selected && clearable && !required) {
      event.preventDefault()
      onValueChange(null)
    }
  }

  function handleWindowClick(event: MouseEvent) {
    if (open && root && !root.contains(event.target as Node))
      close()
  }

  function updateQuery(event: Event) {
    query = (event.currentTarget as HTMLInputElement).value
    activeIndex = 0
    onSearchChange?.(query)
  }

  function handleKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape')
      close()
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      activeIndex = Math.min(activeIndex + 1, filtered.length - 1)
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      activeIndex = Math.max(activeIndex - 1, 0)
    }
    if (event.key === 'Enter' && filtered[activeIndex]) {
      event.preventDefault()
      choose(filtered[activeIndex].id)
    }
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
    {#if selected}<SelectorOptionView option={selected} compact />{:else}<span class='truncate text-muted-foreground'>{placeholder}</span>{/if}
    <span class='ml-auto flex shrink-0 items-center'>
      {#if selected && clearable && !required}
        <span
          aria-label='Clear selection'
          aria-hidden='true'
          class='rounded p-0.5 hover:bg-muted'
          onclick={clearSelection}
        ><X class='size-3.5' /></span>
      {/if}
      <ChevronDown class='size-4 text-muted-foreground' />
    </span>
  </button>

  {#if open}
    <div class='absolute z-50 mt-1 w-full min-w-64 origin-top rounded-md border bg-popover p-1 text-popover-foreground shadow-lg' in:dropdownInTransition out:dropdownOutTransition>
      <div class='flex items-center gap-2 border-b px-2'>
        <Search class='size-4 text-muted-foreground' />
        <input bind:this={searchInput} value={query} oninput={updateQuery} onkeydown={handleKeydown} placeholder={searchPlaceholder} class='h-9 min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground' />
      </div>
      <div class='max-h-52 overflow-y-auto overscroll-contain py-1' role='listbox' aria-busy={loading}>
        {#if loading}
          <SelectorLoading />
        {:else if filtered.length === 0}
          <p class='px-2 py-6 text-center text-sm text-muted-foreground'>{emptyText}</p>
        {:else}
          {#each filtered as option, index (option.id)}
            <button
              type='button'
              role='option'
              aria-selected={option.id === value}
              class={cn('flex w-full items-center justify-between gap-2 rounded-sm px-2 py-2 text-sm hover:bg-accent', index === activeIndex && 'bg-accent')}
              onmouseenter={() => activeIndex = index}
              onclick={() => choose(option.id)}
            >
              <SelectorOptionView {option} />
              {#if option.id === value}<Check class='size-4 shrink-0' />{/if}
            </button>
          {/each}
        {/if}
      </div>
    </div>
  {/if}
</div>

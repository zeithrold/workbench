<script lang='ts'>
  import type { SelectorOption } from './selector-model.js'
  import { cn } from '$lib/utils.js'
  import { Check, ChevronDown, Search, X } from '@lucide/svelte'
  import { dropdownInTransition, dropdownOutTransition } from './dropdown-transition.js'
  import SelectorLoading from './selector-loading.svelte'
  import { filterSelectorOptions, prioritizeSelectedOptions, selectedOptions, toggleSelectorValue } from './selector-model.js'
  import SelectorOptionView from './selector-option.svelte'

  const {
    values,
    options,
    onValuesChange,
    placeholder = 'Select…',
    disabled = false,
    required = false,
    clearable = true,
    maxVisible = 2,
    loading = false,
    onSearchChange,
    class: className,
  }: {
    values: string[]
    options: SelectorOption[]
    onValuesChange: (values: string[]) => void
    placeholder?: string
    disabled?: boolean
    required?: boolean
    clearable?: boolean
    maxVisible?: number
    loading?: boolean
    onSearchChange?: (query: string) => void
    class?: string
  } = $props()
  let open = $state(false)
  let query = $state('')
  let root: HTMLDivElement | null = $state(null)
  const selected = $derived(selectedOptions(options, values))
  const filtered = $derived(prioritizeSelectedOptions(filterSelectorOptions(options, query), values))

  function removeValue(event: MouseEvent, id: string) {
    event.stopPropagation()
    onValuesChange(values.filter(value => value !== id))
  }

  function clearValues(event: MouseEvent) {
    event.stopPropagation()
    onValuesChange([])
  }

  function handleTriggerKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      open = false
      query = ''
      onSearchChange?.('')
      return
    }
    if ((event.key === 'Delete' || event.key === 'Backspace') && values.length && clearable && !required) {
      event.preventDefault()
      onValuesChange([])
    }
  }

  function handleWindowClick(event: MouseEvent) {
    if (open && root && !root.contains(event.target as Node)) {
      open = false
      query = ''
      onSearchChange?.('')
    }
  }

  function updateQuery(event: Event) {
    query = (event.currentTarget as HTMLInputElement).value
    onSearchChange?.(query)
  }
</script>

<svelte:window onclick={handleWindowClick} />

<div bind:this={root} class={cn('relative w-full', className)} data-slot='searchable-multi-select'>
  <button type='button' class='flex min-h-9 w-full items-center gap-1 rounded-md border border-input bg-background px-2 py-1 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50' aria-haspopup='listbox' aria-expanded={open} data-required={required} {disabled} onclick={() => open = !open} onkeydown={handleTriggerKeydown}>
    {#if selected.length === 0}<span class='text-muted-foreground'>{placeholder}</span>{/if}
    {#each selected.slice(0, maxVisible) as option (option.id)}
      <span class='flex max-w-40 items-center gap-1 rounded bg-muted px-1.5 py-0.5 text-xs'>
        <SelectorOptionView {option} compact />
        <span aria-label={`Remove ${option.label}`} aria-hidden='true' onclick={event => removeValue(event, option.id)}><X class='size-3' /></span>
      </span>
    {/each}
    {#if selected.length > maxVisible}<span class='text-xs text-muted-foreground'>+{selected.length - maxVisible}</span>{/if}
    <span class='ml-auto flex items-center'>
      {#if selected.length && clearable && !required}<span aria-label='Clear all selections' aria-hidden='true' class='p-0.5' onclick={clearValues}><X class='size-3.5' /></span>{/if}
      <ChevronDown class='size-4 text-muted-foreground' />
    </span>
  </button>
  {#if open}
    <div class='absolute z-50 mt-1 w-full min-w-64 origin-top rounded-md border bg-popover p-1 shadow-lg' in:dropdownInTransition out:dropdownOutTransition>
      <div class='flex items-center gap-2 border-b px-2'><Search class='size-4 text-muted-foreground' /><input value={query} oninput={updateQuery} aria-label='Search options' class='h-9 min-w-0 flex-1 bg-transparent text-sm outline-none' placeholder='Search…' /></div>
      <div class='max-h-52 overflow-y-auto overscroll-contain py-1' role='listbox' aria-multiselectable='true' aria-busy={loading}>
        {#if loading}
          <SelectorLoading />
        {:else if filtered.length === 0}
          <p class='px-2 py-6 text-center text-sm text-muted-foreground'>No options found</p>
        {:else}
          {#each filtered as option (option.id)}
            <button type='button' role='option' aria-selected={values.includes(option.id)} class='flex w-full items-center justify-between gap-2 rounded-sm px-2 py-2 text-sm hover:bg-accent' onclick={() => onValuesChange(toggleSelectorValue(values, option.id))}>
              <SelectorOptionView {option} />
              {#if values.includes(option.id)}<Check class='size-4 shrink-0' />{/if}
            </button>
          {/each}
        {/if}
      </div>
    </div>
  {/if}
</div>

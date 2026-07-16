<script lang='ts'>
  import type { SelectorOption } from './selector-model.js'
  import * as Command from '$lib/components/ui/command'
  import { ScrollArea } from '$lib/components/ui/scroll-area'
  import { m } from '$lib/paraglide/messages.js'
  import { cn } from '$lib/utils.js'
  import { ChevronDown, X } from '@lucide/svelte'
  import { tick } from 'svelte'
  import { dropdownInTransition } from './dropdown-transition.js'
  import SelectorLoading from './selector-loading.svelte'
  import { filterSelectorOptions, prioritizeSelectedOptions, selectedOptions, toggleSelectorValue } from './selector-model.js'
  import SelectorOptionView from './selector-option.svelte'

  const {
    values,
    options,
    onValuesChange,
    placeholder = m.select(),
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
  let commandValue = $state('')
  let searchInput: HTMLInputElement | null = $state(null)
  let root: HTMLDivElement | null = $state(null)
  const selected = $derived(selectedOptions(options, values))
  const filtered = $derived(prioritizeSelectedOptions(filterSelectorOptions(options, query), values))

  async function show() {
    if (disabled)
      return
    open = true
    commandValue = filtered[0]?.id ?? ''
    await tick()
    searchInput?.focus()
  }

  function close() {
    open = false
    query = ''
    onSearchChange?.('')
  }

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
      close()
      return
    }
    if ((event.key === 'Delete' || event.key === 'Backspace') && values.length && clearable && !required) {
      event.preventDefault()
      onValuesChange([])
    }
  }

  function handleWindowClick(event: MouseEvent) {
    if (open && root && !root.contains(event.target as Node)) {
      close()
    }
  }

  function updateQuery(event: Event) {
    query = (event.currentTarget as HTMLInputElement).value
    commandValue = filtered[0]?.id ?? ''
    onSearchChange?.(query)
  }

  function toggleValue(id: string) {
    onValuesChange(toggleSelectorValue(values, id))
  }
</script>

<svelte:window onclick={handleWindowClick} />

<div bind:this={root} class={cn('relative w-full', className)} data-slot='searchable-multi-select'>
  <button type='button' class='flex min-h-9 w-full items-center gap-1 rounded-md border border-input bg-background px-2 py-1 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50' aria-haspopup='listbox' aria-expanded={open} data-required={required} {disabled} onclick={() => open ? close() : void show()} onkeydown={handleTriggerKeydown}>
    {#if selected.length === 0}<span class='text-muted-foreground'>{placeholder}</span>{/if}
    {#each selected.slice(0, maxVisible) as option (option.id)}
      <span class='flex max-w-40 items-center gap-1 rounded bg-muted px-1.5 py-0.5 text-xs'>
        <SelectorOptionView {option} compact />
        <span aria-label={m.remove_option({ label: option.label })} aria-hidden='true' onclick={event => removeValue(event, option.id)}><X class='size-3' /></span>
      </span>
    {/each}
    {#if selected.length > maxVisible}<span class='text-xs text-muted-foreground'>+{selected.length - maxVisible}</span>{/if}
    <span class='ml-auto flex items-center'>
      {#if selected.length && clearable && !required}<span aria-label={m.clear_all_selections()} aria-hidden='true' class='p-0.5' onclick={clearValues}><X class='size-3.5' /></span>{/if}
      <ChevronDown class='size-4 text-muted-foreground' />
    </span>
  </button>
  {#if open}
    <div class='absolute z-50 mt-1 w-full min-w-64 origin-top rounded-md border bg-popover p-1 shadow-lg' in:dropdownInTransition>
      <Command.Root bind:value={commandValue} shouldFilter={false} loop class='rounded-md p-0' label={m.options()}>
        <Command.Input bind:ref={searchInput} value={query} oninput={updateQuery} placeholder={m.search()} />
        <ScrollArea class='h-52'>
          <Command.List class='max-h-none overflow-visible' aria-busy={loading}>
            {#if loading}
              <SelectorLoading />
            {:else if filtered.length === 0}
              <Command.Empty>{m.no_options_found()}</Command.Empty>
            {:else}
              <Command.Group>
                {#each filtered as option (option.id)}
                  <Command.Item value={option.id} onSelect={() => toggleValue(option.id)} class='w-full py-2' data-checked={values.includes(option.id)}>
                    <SelectorOptionView {option} />
                  </Command.Item>
                {/each}
              </Command.Group>
            {/if}
          </Command.List>
        </ScrollArea>
      </Command.Root>
    </div>
  {/if}
</div>

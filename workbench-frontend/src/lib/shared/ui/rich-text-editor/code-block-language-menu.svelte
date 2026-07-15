<script lang='ts'>
  import type { SvelteMap } from 'svelte/reactivity'
  import { Button } from '$lib/components/ui/button'
  import * as DropdownMenu from '$lib/components/ui/dropdown-menu'
  import { m } from '$lib/paraglide/messages.js'
  import { Check, ChevronDown } from '@lucide/svelte'
  import { CODE_LANGUAGES } from './code-languages.js'

  const { state, onSelect }: {
    state: SvelteMap<string, string>
    onSelect: (language: string) => void
  } = $props()

  const language = $derived(state.get('language') ?? 'plaintext')
  const label = $derived(CODE_LANGUAGES.find(([value]) => value === language)?.[1] ?? m.plain_text())
</script>

<DropdownMenu.Root>
  <DropdownMenu.Trigger>
    {#snippet child({ props })}
      <Button
        {...props}
        variant='ghost'
        size='xs'
        class='h-7 bg-background/85 px-2 text-xs font-normal text-muted-foreground shadow-xs backdrop-blur-sm hover:text-foreground'
        aria-label={m.code_language()}
      >
        {label}
        <ChevronDown class='size-3' />
      </Button>
    {/snippet}
  </DropdownMenu.Trigger>
  <DropdownMenu.Content align='end' class='max-h-72 min-w-40 overflow-y-auto'>
    {#each CODE_LANGUAGES as [value, optionLabel] (value)}
      <DropdownMenu.Item class='justify-between' onSelect={() => onSelect(value)}>
        {optionLabel}
        {#if value === language}<Check class='size-3.5 text-muted-foreground' />{/if}
      </DropdownMenu.Item>
    {/each}
  </DropdownMenu.Content>
</DropdownMenu.Root>

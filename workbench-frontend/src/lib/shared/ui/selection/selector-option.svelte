<script lang='ts'>
  import type { SelectorOption } from './selector-model.js'
  import { cn } from '$lib/utils.js'

  const { option, compact = false }: { option: SelectorOption, compact?: boolean } = $props()
  const initials = $derived(option.label.split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase())
</script>

<span class={cn('flex min-w-0 items-center', compact ? 'gap-1.5' : 'gap-2')}>
  {#if option.avatar}
    <img class={cn('shrink-0 rounded-full object-cover', compact ? 'size-4' : 'size-5')} src={option.avatar} alt='' />
  {:else if option.color}
    <span class={cn('shrink-0 rounded-full', compact ? 'size-2' : 'size-2.5')} style:background-color={option.color}></span>
  {:else}
    <span class={cn('flex shrink-0 items-center justify-center rounded bg-muted font-semibold text-foreground', compact ? 'size-4 text-[8px]' : 'size-5 text-[9px]')}>{initials}</span>
  {/if}
  <span class='min-w-0 text-left'>
    <span class='block truncate'>{option.label}</span>
    {#if option.description && !compact}
      <span class='block truncate text-xs text-muted-foreground'>{option.description}</span>
    {/if}
  </span>
</span>

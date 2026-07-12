<script lang='ts'>
  import type { Snippet } from 'svelte'
  import { cn } from '$lib/utils.js'

  const {
    title,
    description,
    actions,
    class: className,
  }: {
    title: string
    description?: string
    actions?: Snippet
    class?: string
  } = $props()
</script>

<header class={cn('flex flex-wrap items-start justify-between gap-4', className)}>
  <div class='space-y-1'>
    <h1 class='text-2xl font-semibold tracking-tight'>{title}</h1>
    {#if description}
      <p class='text-sm text-muted-foreground'>{description}</p>
    {/if}
  </div>
  {@render actions?.()}
</header>

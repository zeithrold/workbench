<script lang='ts'>
  import type { Snippet } from 'svelte'
  import { cn } from '$lib/utils.js'

  const {
    title,
    description,
    action,
    class: className,
  }: {
    title: string
    description: string
    action?: Snippet
    class?: string
  } = $props()
</script>

<section class={cn('grid min-h-56 place-items-center rounded-xl border border-dashed bg-muted/30 p-8 text-center', className)}>
  <div class='max-w-sm space-y-3'>
    <div class='mx-auto grid size-10 place-items-center rounded-full bg-muted text-muted-foreground'>+</div>
    <div class='space-y-1'>
      <h2 class='font-medium'>{title}</h2>
      <p class='text-sm leading-6 text-muted-foreground'>{description}</p>
    </div>
    {@render action?.()}
  </div>
</section>

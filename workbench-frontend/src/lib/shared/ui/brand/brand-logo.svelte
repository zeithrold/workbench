<script lang='ts'>
  import { cn } from '$lib/utils.js'

  const { class: className }: { class?: string } = $props()
</script>

<div class={cn('flex items-center gap-2 font-semibold tracking-tight', className)} aria-label='Workbench'>
  <span class='grid size-8 place-items-center rounded-lg bg-primary text-primary-foreground'>W</span>
  <span>Workbench</span>
</div>

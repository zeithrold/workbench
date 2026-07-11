<script lang='ts'>
  import type { Snippet } from 'svelte'

  const { label, description, required = false, children }: { label: string, description?: string, required?: boolean, children: Snippet } = $props()
</script>

<div class='grid min-w-0 gap-1.5'>
  <div class='flex items-baseline justify-between gap-3'>
    <span class='text-xs font-medium text-muted-foreground'>{label}{#if required}<span class='ml-0.5 text-destructive' aria-hidden='true'>*</span>{/if}</span>
    {#if description}<span class='truncate text-[11px] text-muted-foreground' title={description}>{description}</span>{/if}
  </div>
  {@render children()}
</div>

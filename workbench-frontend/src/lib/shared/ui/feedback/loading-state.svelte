<script lang='ts'>
  import { m } from '$lib/paraglide/messages.js'
  import { cn } from '$lib/utils.js'

  const { label = m.loading(), class: className }: { label?: string, class?: string } = $props()
</script>

<div class={cn('flex items-center gap-2 text-sm text-muted-foreground', className)} role='status'>
  <span class='size-4 animate-spin rounded-full border-2 border-current border-t-transparent'></span>
  <span>{label}</span>
</div>

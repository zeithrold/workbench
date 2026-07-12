<script lang='ts'>
  import type { HTMLAttributes } from 'svelte/elements'
  import { cn } from '$lib/utils.js'

  const {
    class: className,
    label = 'Loading',
    ...restProps
  }: HTMLAttributes<HTMLDivElement> & { label?: string } = $props()
</script>

<div
  data-slot='skeleton'
  class={cn('animate-pulse rounded-md bg-muted motion-reduce:animate-none', className)}
  role='status'
  aria-label={label}
  {...restProps}
></div>

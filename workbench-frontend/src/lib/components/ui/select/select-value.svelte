<script lang='ts'>
  import type { WithoutChild } from '$lib/utils.js'
  import { cn } from '$lib/utils.js'
  import { Select as SelectPrimitive } from 'bits-ui'

  let {
    ref = $bindable(null),
    class: className,
    placeholder,
    children,
    ...restProps
  }: WithoutChild<SelectPrimitive.ValueProps> = $props()
</script>

{#if children}
  <SelectPrimitive.Value
    bind:ref
    data-slot='select-value'
    {placeholder}
    {children}
    class={cn('flex-1 truncate text-left', className)}
    {...restProps}
  />
{:else}
  <SelectPrimitive.Value
    bind:ref
    data-slot='select-value'
    {placeholder}
    class={cn('flex-1 truncate text-left', className)}
    {...restProps}
  />
{/if}

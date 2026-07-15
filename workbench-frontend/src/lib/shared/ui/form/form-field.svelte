<script lang='ts'>
  import type { Snippet } from 'svelte'
  import { Label } from '$lib/components/ui/label'
  import { cn } from '$lib/utils.js'

  interface ControlContext {
    describedBy: string | undefined
    invalid: boolean
  }

  const {
    id,
    label,
    required = false,
    description,
    error,
    children,
    class: className,
  }: {
    id: string
    label: string
    required?: boolean
    description?: string
    error?: string
    children: Snippet<[ControlContext]>
    class?: string
  } = $props()

  const describedBy = $derived(
    [description ? `${id}-description` : null, error ? `${id}-error` : null]
      .filter(Boolean)
      .join(' ') || undefined,
  )
</script>

<div class={cn('grid gap-2', className)} data-slot='form-field'>
  <Label for={id}>
    {label}
    {#if required}<span class='text-destructive' aria-hidden='true'>*</span>{/if}
  </Label>
  {@render children({ describedBy, invalid: Boolean(error) })}
  {#if description}
    <p id='{id}-description' class='text-xs leading-5 text-muted-foreground'>{description}</p>
  {/if}
  {#if error}
    <p id='{id}-error' class='text-xs leading-5 text-destructive'>{error}</p>
  {/if}
</div>

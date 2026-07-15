<script lang='ts' module>
  import { tv } from 'tailwind-variants'

  export const alertVariants = tv({
    base: 'relative w-full rounded-lg border px-4 py-3 text-sm',
    variants: {
      variant: {
        default: 'bg-card text-card-foreground',
        destructive:
          'border-destructive/40 bg-destructive/10 text-red-700 dark:text-red-300',
      },
    },
    defaultVariants: { variant: 'default' },
  })

  export type AlertVariant = 'default' | 'destructive'
</script>

<script lang='ts'>
  import type { Snippet } from 'svelte'
  import { cn } from '$lib/utils.js'

  const {
    variant = 'default',
    class: className,
    children,
  }: { variant?: AlertVariant, class?: string, children: Snippet } = $props()
</script>

<div
  data-slot='alert'
  class={cn(alertVariants({ variant }), className)}
  role={variant === 'destructive' ? 'alert' : 'status'}
>
  {@render children()}
</div>

<script lang='ts'>
  import * as ToggleGroup from '$lib/components/ui/toggle-group'
  import { m } from '$lib/paraglide/messages.js'
  import ShieldCheckIcon from '@lucide/svelte/icons/shield-check'
  import ShieldXIcon from '@lucide/svelte/icons/shield-x'

  const { value, disabled = false, onChange }: {
    value: 'ALLOW' | 'DENY'
    disabled?: boolean
    onChange: (value: 'ALLOW' | 'DENY') => void
  } = $props()
</script>

<ToggleGroup.Root
  type='single'
  {value}
  {disabled}
  spacing={1}
  class='rounded-none'
  aria-label={m.permission_effect()}
  onValueChange={effect => effect && onChange(effect as 'ALLOW' | 'DENY')}
>
  <ToggleGroup.Item
    value='ALLOW'
    class='border border-transparent text-muted-foreground hover:bg-emerald-500/10 hover:text-emerald-700 data-[state=on]:border-emerald-500/30 data-[state=on]:bg-emerald-500/10 data-[state=on]:text-emerald-700 dark:data-[state=on]:text-emerald-400'
  >
    <ShieldCheckIcon class='size-3.5' />
    {m.permission_allow()}
  </ToggleGroup.Item>
  <ToggleGroup.Item
    value='DENY'
    class='border border-transparent text-muted-foreground hover:bg-destructive/10 hover:text-destructive data-[state=on]:border-destructive/30 data-[state=on]:bg-destructive/10 data-[state=on]:text-destructive'
  >
    <ShieldXIcon class='size-3.5' />
    {m.permission_deny()}
  </ToggleGroup.Item>
</ToggleGroup.Root>

<script lang='ts'>
  import type { DateValue } from '@internationalized/date'
  import { Button } from '$lib/components/ui/button'
  import { Calendar } from '$lib/components/ui/calendar'
  import * as Popover from '$lib/components/ui/popover'
  import { m } from '$lib/paraglide/messages.js'
  import { DateFormatter, getLocalTimeZone, parseDate } from '@internationalized/date'
  import CalendarIcon from '@lucide/svelte/icons/calendar'

  const { value, disabled = false, onChange }: { value?: string, disabled?: boolean, onChange: (value: string) => void } = $props()
  let open = $state(false)
  const parsed = $derived.by(() => {
    try {
      return value ? parseDate(value) : undefined
    }
    catch { return undefined }
  })
  const label = $derived(parsed ? new DateFormatter('en-US', { dateStyle: 'medium' }).format(parsed.toDate(getLocalTimeZone())) : m.permission_choose_date())

  function select(next: DateValue | undefined) {
    if (!next)
      return
    onChange(next.toString())
    open = false
  }
</script>

<Popover.Root bind:open>
  <Popover.Trigger>
    {#snippet child({ props })}
      <Button {...props} type='button' variant='outline' class='w-full justify-start font-normal' {disabled} aria-label={m.permission_value()}>
        <CalendarIcon class='size-3.5' />{label}
      </Button>
    {/snippet}
  </Popover.Trigger>
  <Popover.Content class='w-auto p-0' align='start'>
    <Calendar type='single' value={parsed} onValueChange={select} captionLayout='dropdown' />
  </Popover.Content>
</Popover.Root>

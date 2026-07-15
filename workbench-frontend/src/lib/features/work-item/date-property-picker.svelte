<script lang='ts'>
  import type { DateValue } from '@internationalized/date'
  import type { WorkItemScalarValue } from './selector-types.js'
  import { Button } from '$lib/components/ui/button'
  import { Calendar } from '$lib/components/ui/calendar'
  import * as Popover from '$lib/components/ui/popover'
  import { localeState } from '$lib/i18n/locale.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { cn } from '$lib/utils.js'
  import { CalendarDateTime, DateFormatter, getLocalTimeZone, parseAbsoluteToLocal, parseDate } from '@internationalized/date'
  import { CalendarIcon, ChevronDown, X } from '@lucide/svelte'

  const { value = null, onValueChange, includeTime = false, disabled = false, required = false, label }: {
    value?: WorkItemScalarValue
    onValueChange: (value: WorkItemScalarValue) => void
    includeTime?: boolean
    disabled?: boolean
    required?: boolean
    label: string
  } = $props()

  let open = $state(false)
  const dateValue = $derived.by((): DateValue | undefined => {
    if (typeof value !== 'string' || !value)
      return undefined
    return runCatchingDate(value, includeTime)
  })
  const hour = $derived(includeTime && dateValue && 'hour' in dateValue ? dateValue.hour : 9)
  const minute = $derived(includeTime && dateValue && 'minute' in dateValue ? dateValue.minute : 0)
  const formatter = $derived(new DateFormatter(localeState.current, includeTime
    ? { dateStyle: 'medium', timeStyle: 'short' }
    : { dateStyle: 'medium' }))
  const hours = Array.from({ length: 24 }, (_, index) => index)
  const minutes = [0, 15, 30, 45]
  const displayValue = $derived(dateValue ? formatter.format(dateValue.toDate(getLocalTimeZone())) : undefined)

  function selectDate(next: DateValue | undefined) {
    if (!next)
      return
    if (!includeTime) {
      onValueChange(next.toString())
      open = false
      return
    }
    onValueChange(toIso(next, hour, minute))
  }

  function selectTime(nextHour: number, nextMinute: number) {
    if (dateValue)
      onValueChange(toIso(dateValue, nextHour, nextMinute))
  }

  function clear(event: MouseEvent) {
    event.stopPropagation()
    onValueChange(null)
    open = false
  }

  function runCatchingDate(raw: string, withTime: boolean): DateValue | undefined {
    try {
      return withTime ? parseAbsoluteToLocal(raw) : parseDate(raw)
    }
    catch {
      return undefined
    }
  }

  function toIso(date: DateValue, selectedHour: number, selectedMinute: number): string {
    return new CalendarDateTime(date.year, date.month, date.day, selectedHour, selectedMinute)
      .toDate(getLocalTimeZone())
      .toISOString()
  }
</script>

<Popover.Root bind:open>
  <Popover.Trigger>
    {#snippet child({ props })}
      <Button {...props} variant='outline' class={cn('w-full justify-start font-normal', !displayValue && 'text-muted-foreground')} {disabled} aria-label={label}>
        <CalendarIcon class='size-4' />
        <span class='truncate'>{displayValue ?? (includeTime ? m.select_date_and_time() : m.select_date())}</span>
        <span class='ml-auto flex items-center gap-1'>
          {#if displayValue && !required}
            <span aria-hidden='true' class='rounded p-0.5 hover:bg-muted' onclick={clear}><X class='size-3.5' /></span>
          {/if}
          <ChevronDown class='size-4 text-muted-foreground' />
        </span>
      </Button>
    {/snippet}
  </Popover.Trigger>
  <Popover.Content class='w-auto gap-0 overflow-hidden p-0' align='start'>
    <Calendar type='single' value={dateValue} onValueChange={selectDate} captionLayout='dropdown' />
    {#if includeTime}
      <div class='flex items-center gap-2 border-t p-3'>
        <span class='text-xs font-medium text-muted-foreground'>{m.time()}</span>
        <select aria-label={m.hour()} class='h-8 rounded-md border bg-background px-2 text-sm' value={hour} onchange={event => selectTime(Number(event.currentTarget.value), minute)} disabled={!dateValue}>
          {#each hours as option (option)}<option value={option}>{String(option).padStart(2, '0')}</option>{/each}
        </select>
        <span>:</span>
        <select aria-label={m.minute()} class='h-8 rounded-md border bg-background px-2 text-sm' value={minute} onchange={event => selectTime(hour, Number(event.currentTarget.value))} disabled={!dateValue}>
          {#each minutes as option (option)}<option value={option}>{String(option).padStart(2, '0')}</option>{/each}
        </select>
        <Button size='sm' class='ml-auto' disabled={!dateValue} onclick={() => open = false}>{m.done()}</Button>
      </div>
    {/if}
  </Popover.Content>
</Popover.Root>

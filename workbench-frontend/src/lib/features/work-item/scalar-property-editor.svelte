<script lang='ts'>
  import type { WorkItemPropertyDefinition, WorkItemScalarValue } from './selector-types.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Input } from '$lib/shared/ui'
  import DatePropertyPicker from './date-property-picker.svelte'
  import { numberConstraint, stringConstraint, validateScalarValue } from './property-value.js'

  const { definition, value = null, onValueChange, disabled = false, required = false }: {
    definition: WorkItemPropertyDefinition
    value?: WorkItemScalarValue
    onValueChange: (value: WorkItemScalarValue) => void
    disabled?: boolean
    required?: boolean
  } = $props()

  const error = $derived(validateScalarValue(definition, value, required))
  const errorId = $derived(`${definition.id}-error`)
  const inputType = $derived(definition.dataType === 'url' ? 'url' : definition.dataType)

  function updateInput(event: Event) {
    const input = event.currentTarget as HTMLInputElement
    if (definition.dataType === 'number') {
      onValueChange(input.value === '' ? null : input.valueAsNumber)
      return
    }
    onValueChange(input.value || null)
  }
</script>

{#if definition.dataType === 'date' || definition.dataType === 'datetime'}
  <DatePropertyPicker {value} {onValueChange} includeTime={definition.dataType === 'datetime'} {disabled} {required} label={definition.name} />
{:else if definition.dataType === 'boolean'}
  <button
    type='button'
    role='switch'
    aria-label={definition.name}
    aria-checked={value === true}
    class='flex h-9 w-full items-center justify-between rounded-md border border-input bg-background px-2.5 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50'
    {disabled}
    onclick={() => onValueChange(value !== true)}
  >
    <span>{value === true ? m.yes() : m.no()}</span>
    <span class='relative h-5 w-9 rounded-full bg-muted transition-colors data-[checked=true]:bg-primary' data-checked={value === true}>
      <span class='absolute left-0.5 top-0.5 size-4 rounded-full bg-background shadow transition-transform data-[checked=true]:translate-x-4' data-checked={value === true}></span>
    </span>
  </button>
{:else}
  <Input
    type={inputType}
    value={value ?? ''}
    min={definition.dataType === 'number' ? numberConstraint(definition.validationSchema, 'minimum') : undefined}
    max={definition.dataType === 'number' ? numberConstraint(definition.validationSchema, 'maximum') : undefined}
    step={definition.dataType === 'number' ? numberConstraint(definition.validationSchema, 'multipleOf') : undefined}
    minlength={stringConstraint(definition.validationSchema, 'minLength')}
    maxlength={stringConstraint(definition.validationSchema, 'maxLength')}
    placeholder={definition.dataType === 'url' ? 'https://example.com' : `Enter ${definition.name.toLowerCase()}`}
    aria-label={definition.name}
    aria-invalid={error ? 'true' : undefined}
    aria-describedby={error ? errorId : undefined}
    {disabled}
    {required}
    oninput={updateInput}
  />
{/if}
{#if error}<p id={errorId} class='text-xs text-destructive'>{error}</p>{/if}

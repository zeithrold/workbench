<script lang='ts'>
  import type { SelectorOption } from '$lib/shared/ui'
  import type { PermissionCondition, PermissionConditionOperator } from './permission-document.js'
  import type { PermissionFieldOption } from './permission-editor-model.js'
  import * as Field from '$lib/components/ui/field'
  import { Input } from '$lib/components/ui/input'
  import * as Select from '$lib/components/ui/select'
  import * as ToggleGroup from '$lib/components/ui/toggle-group'
  import { m } from '$lib/paraglide/messages.js'
  import { Badge, SearchableSelect } from '$lib/shared/ui'
  import PermissionDateEditor from './permission-date-editor.svelte'
  import { normalizePermissionOperator, normalizePermissionPredicate, permissionFieldId, permissionFieldTypeLabel, permissionOperatorLabel, permissionPredicateErrors, permissionValueEditorKind } from './permission-editor-model.js'
  import PermissionValueSelector from './permission-value-selector.svelte'

  const { node, fields, editable = true, onChange }: {
    node: Extract<PermissionCondition, { field: unknown }>
    fields: PermissionFieldOption[]
    editable?: boolean
    onChange: (node: PermissionCondition) => void
  } = $props()

  const selectedField = $derived(fields.find(field => permissionFieldId(field.field) === permissionFieldId(node.field)))
  const errors = $derived(permissionPredicateErrors(node, fields))
  const editorKind = $derived(selectedField ? permissionValueEditorKind(selectedField, node.op) : 'none')
  const valueOptions = $derived(selectedField?.values ?? [])
  const operatorItems = $derived((selectedField?.operators ?? []).map(operator => ({
    value: operator,
    label: permissionOperatorLabel(operator),
  })))

  function selectField(id: string | null) {
    const field = fields.find(item => item.id === id)
    if (field && field.id !== selectedField?.id)
      onChange(normalizePermissionPredicate(node, field))
  }

  function selectOperator(operator: PermissionConditionOperator) {
    if (!selectedField || operator === node.op)
      return
    onChange(normalizePermissionOperator(node, selectedField, operator))
  }
</script>

{#snippet fieldTriggerContent(option: SelectorOption)}
  {@const field = fields.find(item => item.id === option.id)}
  <span class='flex min-w-0 items-center gap-2'><span class='truncate'>{option.label}</span>{#if field}<Badge variant='outline' class='shrink-0'>{permissionFieldTypeLabel(field.type)}</Badge>{/if}</span>
{/snippet}

{#snippet fieldOptionContent(option: SelectorOption)}
  {@const field = fields.find(item => item.id === option.id)}
  <span class='flex min-w-0 flex-1 items-center justify-between gap-3 text-left'><span class='min-w-0'><span class='block truncate'>{option.label}</span>{#if option.description}<span class='block truncate text-xs text-muted-foreground'>{option.description}</span>{/if}</span>{#if field}<Badge variant='outline' class='shrink-0'>{permissionFieldTypeLabel(field.type)}</Badge>{/if}</span>
{/snippet}

<div class='grid gap-3 md:grid-cols-[minmax(12rem,1.2fr)_minmax(10rem,0.8fr)_minmax(12rem,1.2fr)]' data-node-id={node.uiId}>
  <Field.Field>
    <Field.Label>{m.permission_field()}</Field.Label>
    <SearchableSelect value={selectedField?.id} options={fields} disabled={!editable} required clearable={false} placeholder={m.permission_choose_field()} triggerContent={fieldTriggerContent} optionContent={fieldOptionContent} onValueChange={selectField} />
  </Field.Field>
  <Field.Field>
    <Field.Label>{m.permission_operator()}</Field.Label>
    <Select.Root type='single' items={operatorItems} value={node.op} onValueChange={value => value && selectOperator(value as PermissionConditionOperator)} disabled={!editable}>
      <Select.Trigger class='w-full' indicatorClass='size-3'><Select.Value placeholder={m.permission_operator()} /></Select.Trigger>
      <Select.Content>{#each selectedField?.operators ?? [] as operator (operator)}<Select.Item value={operator} label={permissionOperatorLabel(operator)} indicatorClass='size-3'>{permissionOperatorLabel(operator)}</Select.Item>{/each}</Select.Content>
    </Select.Root>
  </Field.Field>
  {#if editorKind !== 'none'}
    <Field.Field data-invalid={errors.length > 0}>
      <Field.Label>{m.permission_value()}</Field.Label>
      {#if editorKind === 'single'}
        <PermissionValueSelector value={node.value} options={valueOptions} disabled={!editable} onChange={value => onChange({ ...node, value })} />
      {:else if editorKind === 'multi'}
        <PermissionValueSelector value={node.value} options={valueOptions} multiple disabled={!editable} onChange={value => onChange({ ...node, value })} />
      {:else if editorKind === 'boolean'}
        <ToggleGroup.Root type='single' value={String(node.value === true)} spacing={1} class='rounded-none' aria-label={m.permission_value()} disabled={!editable} onValueChange={value => value && onChange({ ...node, value: value === 'true' })}>
          <ToggleGroup.Item value='true'>{m.yes()}</ToggleGroup.Item><ToggleGroup.Item value='false'>{m.no()}</ToggleGroup.Item>
        </ToggleGroup.Root>
      {:else if editorKind === 'date'}
        <PermissionDateEditor value={typeof node.value === 'string' ? node.value : undefined} disabled={!editable} onChange={value => onChange({ ...node, value })} />
      {:else}
        <Input type={editorKind === 'number' ? 'number' : 'text'} value={typeof node.value === 'string' || typeof node.value === 'number' ? node.value : ''} disabled={!editable} oninput={event => onChange({ ...node, value: editorKind === 'number' ? Number(event.currentTarget.value) : event.currentTarget.value })} />
      {/if}
      <Field.Error errors={errors.map(message => ({ message }))} />
    </Field.Field>
  {/if}
</div>
